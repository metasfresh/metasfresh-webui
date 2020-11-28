package de.metas.ui.web;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.adempiere.ad.migration.logger.IMigrationLogger;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.service.ISysConfigBL;
import org.adempiere.util.lang.IAutoCloseable;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.compiere.Adempiere;
import org.compiere.Adempiere.RunMode;
import org.compiere.model.ModelValidationEngine;
import org.compiere.util.Env;
import org.compiere.util.Ini;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.metas.JsonObjectMapperHolder;
import de.metas.MetasfreshBeanNameGenerator;
import de.metas.Profiles;
import de.metas.ui.web.base.model.I_T_WEBUI_ViewSelection;
import de.metas.ui.web.session.WebRestApiContextProvider;
import de.metas.ui.web.window.model.DocumentInterfaceWrapperHelper;
import de.metas.util.Check;
import de.metas.util.Services;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@SpringBootApplication(scanBasePackages = { "de.metas", "org.adempiere" })
@EnableAsync
@Profile(Profiles.PROFILE_Webui)
public class WebRestApiApplication
{
	private static final String SYSCONFIG_PREFIX_WEBUI_SPRING_PROFILES_ACTIVE = "de.metas.ui.web.spring.profiles.active";

	public static final String BEANNAME_WebuiTaskScheduler = "webuiTaskScheduler";

	/**
	 * By default, we run in headless mode. But using this system property, we can also run with headless=false.
	 * The only known use of that is that metasfresh can open the initial license & connection dialog to store the initial properties file.
	 */
	private static final String SYSTEM_PROPERTY_HEADLESS = "webui-api-run-headless";

	@Autowired
	private ApplicationContext applicationContext;

	public static void main(final String[] args)
	{
		if (Check.isEmpty(System.getProperty("PropertyFile"), true))
		{
			System.setProperty("PropertyFile", "./metasfresh.properties");
		}

		// Make sure slf4j is used (by default, in v2.4.4 log4j is used
		// https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/_using_another_logger.html

		try (final IAutoCloseable c = ModelValidationEngine.postponeInit())
		{
			Ini.setRunMode(RunMode.WEBUI);
			Adempiere.instance.startup(RunMode.WEBUI);

			final ArrayList<String> activeProfiles = retrieveActiveProfilesFromSysConfig();
			activeProfiles.add(Profiles.PROFILE_Webui);

			final String headless = System.getProperty(SYSTEM_PROPERTY_HEADLESS, Boolean.toString(true));

			new SpringApplicationBuilder(WebRestApiApplication.class)
					.headless(Boolean.parseBoolean(headless)) // we need headless=false for initial connection setup popup (if any), usually this only applies on dev workstations.
					.web(WebApplicationType.SERVLET)
					.profiles(activeProfiles.toArray(new String[0]))
					.beanNameGenerator(new MetasfreshBeanNameGenerator())
					.run(args);
		}

		// now init the model validation engine
		ModelValidationEngine.get();
	}

	private static ArrayList<String> retrieveActiveProfilesFromSysConfig()
	{
		final ArrayList<String> activeProfiles = Services
				.get(ISysConfigBL.class)
				.getValuesForPrefix(SYSCONFIG_PREFIX_WEBUI_SPRING_PROFILES_ACTIVE, 0, 0)
				.entrySet()
				.stream()
				.map(Entry::getValue)
				.collect(Collectors.toCollection(ArrayList::new));
		return activeProfiles;
	}

	@Bean
	@Primary
	public ObjectMapper jsonObjectMapper()
	{
		return JsonObjectMapperHolder.sharedJsonObjectMapper();
	}

	@Bean(Adempiere.BEAN_NAME)
	public Adempiere adempiere(final WebRestApiContextProvider webuiContextProvider)
	{
		Env.setContextProvider(webuiContextProvider);

		AdempiereException.enableCaptureLanguageOnConstructionTime(); // because usually at the time the message is (lazy) parsed the user session context is no longer available.

		InterfaceWrapperHelper.registerHelper(new DocumentInterfaceWrapperHelper());

		Services.get(IMigrationLogger.class).addTableToIgnoreList(I_T_WEBUI_ViewSelection.Table_Name);

		final Adempiere adempiere = Env.getSingleAdempiereInstance(applicationContext);
		return adempiere;
	}

	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webServerFactoryCustomizer()
	{
		return new WebServerFactoryCustomizer<TomcatServletWebServerFactory>()
		{
			@Override
			public void customize(@NonNull final TomcatServletWebServerFactory factory)
			{ // could probably be done via functional interface, but this way imho it's clearer..in ten years when we need to adopt to another breaking API change.
				factory.addConnectorCustomizers(connector -> {
					final AbstractHttp11Protocol<?> httpProtocol = (AbstractHttp11Protocol<?>)connector.getProtocolHandler();
					httpProtocol.setCompression("on");
					httpProtocol.setCompressionMinSize(256);
					final String mimeTypes = httpProtocol.getCompressibleMimeType();
					final String mimeTypesWithJson = mimeTypes + "," + MediaType.APPLICATION_JSON_VALUE + ",application/javascript";
					httpProtocol.setCompressibleMimeType(mimeTypesWithJson);

				});
			}
		};
	}

	@Bean(BEANNAME_WebuiTaskScheduler)
	public TaskScheduler webuiTaskScheduler()
	{
		final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setThreadNamePrefix("webui-task-scheduler-");
		taskScheduler.setDaemon(true);
		taskScheduler.setPoolSize(10);
		return taskScheduler;
	}
}
