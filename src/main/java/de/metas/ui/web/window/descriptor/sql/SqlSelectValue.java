package de.metas.ui.web.window.descriptor.sql;

import java.util.Objects;

import de.metas.util.Check;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2020 metas GmbH
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
 * SQL to be used in expressions like <code>SELECT ... 'this field's sql' ... FROM ...</code>
 */
@EqualsAndHashCode
@ToString
public class SqlSelectValue
{
	private final String tableNameOrAlias;
	private final String columnName;
	private final String virtualColumnSql;
	private final String columnNameAlias;

	@Builder(toBuilder = true)
	private SqlSelectValue(
			final String tableNameOrAlias,
			final String columnName,
			final String virtualColumnSql,
			@NonNull final String columnNameAlias)
	{
		this.columnNameAlias = columnNameAlias;

		if (!Check.isEmpty(virtualColumnSql, true))
		{
			this.tableNameOrAlias = null;
			this.columnName = null;
			this.virtualColumnSql = virtualColumnSql;

		}
		else
		{
			Check.assumeNotEmpty(columnName, "columnName is not empty");

			this.tableNameOrAlias = Check.isNotBlank(tableNameOrAlias) ? tableNameOrAlias : null;
			this.columnName = columnName;
			this.virtualColumnSql = null;
		}
	}

	public String toSqlStringWithColumnNameAlias()
	{
		return toSqlString() + " AS " + columnNameAlias;
	}

	public String toSqlString()
	{
		if (virtualColumnSql != null)
		{
			return virtualColumnSql;
		}
		else if (tableNameOrAlias != null)
		{
			return tableNameOrAlias + "." + columnName;
		}
		else
		{
			return columnName;
		}
	}

	public SqlSelectValue withJoinOnTableNameOrAlias(final String tableNameOrAlias)
	{
		final String tableNameOrAliasEffective = virtualColumnSql != null ? tableNameOrAlias : null;
		return !Objects.equals(this.tableNameOrAlias, tableNameOrAliasEffective)
				? toBuilder().tableNameOrAlias(tableNameOrAliasEffective).build()
				: this;
	}
}
