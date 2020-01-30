package de.metas.ui.web.document.filter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.adempiere.exceptions.AdempiereException;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import de.metas.i18n.ITranslatableString;
import de.metas.i18n.TranslatableStrings;
import de.metas.ui.web.document.filter.DocumentFilterParam.Operator;
import de.metas.util.Check;
import de.metas.util.GuavaCollectors;
import de.metas.util.lang.RepoIdAware;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

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

/**
 * Also see {@link de.metas.ui.web.document.filter.sql.SqlDocumentFilterConverter}.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
@Immutable
@EqualsAndHashCode
@ToString
public final class DocumentFilter
{
	public static Builder builder()
	{
		return new Builder();
	}

	public static DocumentFilter singleParameterFilter(final String filterId, final String fieldName, final Operator operator, final Object value)
	{
		return builder()
				.setFilterId(filterId)
				.addParameter(DocumentFilterParam.builder()
						.setFieldName(fieldName)
						.setOperator(operator)
						.setValue(value)
						.build())
				.build();
	}

	public static DocumentFilter inArrayFilter(@NonNull final String filterId, @NonNull final String fieldName, @NonNull final Collection<Integer> values)
	{
		Check.assumeNotEmpty(values, "values is not empty");

		return builder()
				.setFilterId(filterId)
				.addParameter(DocumentFilterParam.builder()
						.setFieldName(fieldName)
						.setOperator(Operator.IN_ARRAY)
						.setValue(ImmutableList.copyOf(values))
						.build())
				.build();
	}

	@Getter
	private final String filterId;
	private final ITranslatableString caption;
	private final ImmutableMap<String, DocumentFilterParam> parametersByName;
	private final ImmutableSet<String> internalParameterNames;
	@Getter
	private final boolean facetFilter;

	private DocumentFilter(final Builder builder)
	{
		filterId = builder.filterId;
		Check.assumeNotEmpty(filterId, "filterId is not empty");

		caption = builder.caption;

		facetFilter = builder.facetFilter;

		parametersByName = builder.parametersByName != null ? ImmutableMap.copyOf(builder.parametersByName) : ImmutableMap.of();
		internalParameterNames = builder.internalParameterNames != null ? ImmutableSet.copyOf(builder.internalParameterNames) : ImmutableSet.of();
	}

	public String getCaption(@Nullable final String adLanguage)
	{
		return caption != null ? caption.translate(adLanguage) : null;
	}

	public boolean hasParameters()
	{
		return !parametersByName.isEmpty();
	}

	public ImmutableCollection<DocumentFilterParam> getParameters()
	{
		return parametersByName.values();
	}

	public boolean isInternalParameter(final String parameterName)
	{
		return internalParameterNames.contains(parameterName);
	}

	public DocumentFilterParam getParameter(@NonNull final String parameterName)
	{
		final DocumentFilterParam parameter = getParameterOrNull(parameterName);
		if (parameter == null)
		{
			throw new AdempiereException("Parameter " + parameterName + " not found in " + this);
		}
		return parameter;
	}

	public DocumentFilterParam getParameterOrNull(@NonNull final String parameterName)
	{
		return parametersByName.get(parameterName);
	}

	public String getParameterValueAsString(@NonNull final String parameterName)
	{
		final DocumentFilterParam param = getParameter(parameterName);
		return param.getValueAsString();
	}

	public String getParameterValueAsString(@NonNull final String parameterName, final String defaultValue)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return defaultValue;
		}

		return param.getValueAsString();
	}

	public int getParameterValueAsInt(@NonNull final String parameterName, final int defaultValue)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return defaultValue;
		}

		return param.getValueAsInt(defaultValue);
	}

	public Boolean getParameterValueAsBoolean(@NonNull final String parameterName, final Boolean defaultValue)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return defaultValue;
		}

		return param.getValueAsBoolean(defaultValue);
	}

	public boolean getParameterValueAsBoolean(@NonNull final String parameterName, final boolean defaultValue)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return defaultValue;
		}

		return param.getValueAsBoolean(defaultValue);
	}

	public LocalDate getParameterValueAsLocalDateOrNull(@NonNull final String parameterName)
	{
		final LocalDate defaultValue = null;
		return getParameterValueAsLocalDateOr(parameterName, defaultValue);
	}

	public LocalDate getParameterValueAsLocalDateOr(@NonNull final String parameterName, final LocalDate defaultValue)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return defaultValue;
		}

		return param.getValueAsLocalDateOr(defaultValue);
	}

	public <T extends RepoIdAware> T getParameterValueAsRepoIdOrNull(@NonNull final String parameterName, @NonNull IntFunction<T> repoIdMapper)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return null;
		}

		return param.getValueAsRepoIdOrNull(repoIdMapper);
	}

	public <T> T getParameterValueAs(@NonNull final String parameterName)
	{
		final DocumentFilterParam param = getParameterOrNull(parameterName);
		if (param == null)
		{
			return null;
		}

		@SuppressWarnings("unchecked")
		final T value = (T)param.getValue();
		return value;
	}

	//
	//
	//
	//
	//

	public static final class Builder
	{
		private String filterId;
		private ITranslatableString caption = TranslatableStrings.empty();
		private boolean facetFilter;

		private LinkedHashMap<String, DocumentFilterParam> parametersByName;
		private Set<String> internalParameterNames;

		private Builder()
		{
		}

		public DocumentFilter build()
		{
			return new DocumentFilter(this);
		}

		public Builder setFilterId(final String filterId)
		{
			this.filterId = filterId;
			return this;
		}

		public Builder setCaption(@NonNull final ITranslatableString caption)
		{
			this.caption = caption;
			return this;
		}

		public Builder setCaption(@NonNull final String caption)
		{
			return setCaption(TranslatableStrings.constant(caption));
		}

		public Builder setFacetFilter(final boolean facetFilter)
		{
			this.facetFilter = facetFilter;
			return this;
		}

		public boolean hasParameters()
		{
			return !Check.isEmpty(parametersByName)
					|| !Check.isEmpty(internalParameterNames);
		}

		public Builder setParameters(@NonNull final List<DocumentFilterParam> parameters)
		{
			if (!parameters.isEmpty())
			{
				this.parametersByName = parameters
						.stream()
						.collect(GuavaCollectors.toMapByKey(LinkedHashMap::new, DocumentFilterParam::getFieldName));
			}

			return this;
		}

		public Builder addParameter(@NonNull final DocumentFilterParam parameter)
		{
			if (parametersByName == null)
			{
				parametersByName = new LinkedHashMap<>();
			}

			final String fieldName = parameter.getFieldName();
			final DocumentFilterParam alreadyAddedParam = parametersByName.get(fieldName);
			if (alreadyAddedParam != null)
			{
				throw new AdempiereException("Cannot add " + parameter + " because a parameter with same name was already added: " + alreadyAddedParam);
			}

			parametersByName.put(fieldName, parameter);
			return this;
		}

		public Builder addInternalParameter(@NonNull final DocumentFilterParam parameter)
		{
			addParameter(parameter);
			addInternalParameterName(parameter.getFieldName());
			return this;
		}

		private void addInternalParameterName(final String parameterName)
		{
			if (internalParameterNames == null)
			{
				internalParameterNames = new HashSet<>();
			}
			internalParameterNames.add(parameterName);
		}
	}
}
