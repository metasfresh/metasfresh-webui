package de.metas.ui.web.document.filter.sql;

import javax.annotation.Nullable;

import org.adempiere.ad.dao.IQueryFilter;
import org.adempiere.ad.dao.impl.TypedSqlQueryFilter;
import org.compiere.util.DB;

import de.metas.printing.esb.base.util.Check;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilterList;
import de.metas.ui.web.window.model.sql.SqlOptions;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
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
 * Converts a {@link DocumentFilter} to SQL.
 * 
 * To create and manipulate {@link SqlDocumentFilterConverter}s please use {@link SqlDocumentFilterConverters}.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
public interface SqlDocumentFilterConverter
{
	/** @return true if the filter identified by <code>filterId</code> can be converted to SQL by this converter */
	boolean canConvert(final String filterId);

	/**
	 * Converts given <code>filter</code> to SQL and returns it.
	 * In case the produced SQL requires parameters, those parameters will be added to <code>sqlParamsOut</code> parameter.
	 *
	 * @return SQL
	 */
	@Nullable
	String getSql(SqlParamsCollector sqlParamsOut,
			DocumentFilter filter,
			final SqlOptions sqlOpts,
			final SqlDocumentFilterConverterContext context);

	/* final */ default String getSql(
			@NonNull final SqlParamsCollector sqlParamsOut,
			@NonNull final DocumentFilterList filters,
			@NonNull final SqlOptions sqlOpts,
			@NonNull final SqlDocumentFilterConverterContext context)
	{
		if (filters.isEmpty())
		{
			return "";
		}

		final StringBuilder sqlWhereClauseBuilder = new StringBuilder();

		for (final DocumentFilter filter : filters.toList())
		{
			final String sqlFilter = getSql(sqlParamsOut, filter, sqlOpts, context);
			if (Check.isEmpty(sqlFilter, true))
			{
				continue;
			}

			if (sqlWhereClauseBuilder.length() > 0)
			{
				sqlWhereClauseBuilder.append("\n AND ");
			}
			sqlWhereClauseBuilder.append(DB.TO_COMMENT(filter.getFilterId())).append("(").append(sqlFilter).append(")");
		}

		return sqlWhereClauseBuilder.toString();
	}

	default <T> IQueryFilter<T> createQueryFilter(
			@NonNull final DocumentFilterList filters,
			@NonNull final SqlOptions sqlOpts,
			@NonNull final SqlDocumentFilterConverterContext context)
	{
		final SqlParamsCollector sqlFilterParams = SqlParamsCollector.newInstance();
		final String sqlFilter = getSql(sqlFilterParams, filters, sqlOpts, context);
		return TypedSqlQueryFilter.of(sqlFilter, sqlFilterParams.toList());
	}
}
