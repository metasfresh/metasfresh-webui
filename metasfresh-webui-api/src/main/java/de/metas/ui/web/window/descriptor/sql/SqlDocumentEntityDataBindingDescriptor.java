package de.metas.ui.web.window.descriptor.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.adempiere.ad.expression.api.ICachedStringExpression;
import org.adempiere.ad.expression.api.IExpressionFactory;
import org.adempiere.ad.expression.api.IStringExpression;
import org.adempiere.ad.expression.api.impl.CompositeStringExpression;
import org.adempiere.ad.security.IUserRolePermissions;
import org.adempiere.ad.security.impl.AccessSqlStringExpression;
import org.adempiere.util.Check;
import org.adempiere.util.GuavaCollectors;
import org.adempiere.util.Services;
import org.adempiere.util.lang.IPair;
import org.compiere.model.POInfo;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import de.metas.ui.web.view.descriptor.SqlDocumentViewBinding;
import de.metas.ui.web.window.descriptor.DetailId;
import de.metas.ui.web.window.descriptor.DocumentEntityDataBindingDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDataBindingDescriptor;
import de.metas.ui.web.window.model.DocumentQueryOrderBy;
import de.metas.ui.web.window.model.DocumentsRepository;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public final class SqlDocumentEntityDataBindingDescriptor implements DocumentEntityDataBindingDescriptor
{
	public static final Builder builder()
	{
		return new Builder();
	}

	public static final SqlDocumentEntityDataBindingDescriptor cast(final DocumentEntityDataBindingDescriptor descriptor)
	{
		return (SqlDocumentEntityDataBindingDescriptor)descriptor;
	}

	private static final String TABLEALIAS_Master = "master";

	public static final String FIELDNAME_Version = "Updated";

//	//
//	// Paging constants
//	public static final String COLUMNNAME_Paging_UUID = "_sel_UUID";
//	public static final String COLUMNNAME_Paging_SeqNo = "_sel_SeqNo";
//	public static final String COLUMNNAME_Paging_Record_ID = "_sel_Record_ID";

	private final DocumentsRepository documentsRepository;
	private final String sqlTableName;
	private final String sqlTableAlias;
	private final String sqlKeyColumnName;
	private final String sqlLinkColumnName;
	private final String sqlParentLinkColumnName;

	private final ICachedStringExpression sqlSelectAllFrom;
	private final ICachedStringExpression sqlWhereClause;
	private final List<DocumentQueryOrderBy> orderBys;

	private final Map<String, SqlDocumentFieldDataBindingDescriptor> _fieldsByFieldName;

	private final SqlDocumentViewBinding documentViewBinding;
	
	private final Optional<String> sqlSelectVersionById;

	private SqlDocumentEntityDataBindingDescriptor(final Builder builder)
	{
		super();

		documentsRepository = builder.getDocumentsRepository();

		sqlTableName = builder.getTableName();
		Check.assumeNotEmpty(sqlTableName, "sqlTableName is not empty");

		sqlTableAlias = builder.getTableAlias();
		Check.assumeNotEmpty(sqlTableAlias, "sqlTableAlias is not empty");

		sqlKeyColumnName = builder.getKeyColumnName();

		sqlLinkColumnName = builder.getSqlLinkColumnName();
		sqlParentLinkColumnName = builder.getSqlParentLinkColumnName();

		_fieldsByFieldName = ImmutableMap.copyOf(builder.getFieldsByFieldName());

		sqlSelectAllFrom = builder.getSqlSelectAll()
				.caching();
		sqlWhereClause = builder.getSqlWhereClauseExpression()
				.caching();

		orderBys = ImmutableList.copyOf(builder.getOrderBysList());
		
		sqlSelectVersionById = builder.getSqlSelectVersionById();
		
		documentViewBinding = builder.buildDocumentViewBinding();
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("sqlTableName", sqlTableName)
				.add("sqlTableAlias", sqlTableAlias)
				.add("sqlKeyColumnName", sqlKeyColumnName)
				.add("sqlLinkColumnName", sqlLinkColumnName)
				.add("sqlParentLinkColumnName", sqlParentLinkColumnName)
				.add("orderBys", orderBys)
				.add("fields", _fieldsByFieldName.isEmpty() ? null : _fieldsByFieldName.values())
				.toString();
	}

	@Override
	public DocumentsRepository getDocumentsRepository()
	{
		return documentsRepository;
	}

	public String getTableName()
	{
		return sqlTableName;
	}

	public String getTableAlias()
	{
		return sqlTableAlias;
	}

	public POInfo getPOInfo()
	{
		// NOTE: don't cache it here because it might change dynamically and it would be so nice to support that case...
		return POInfo.getPOInfo(sqlTableName);
	}

	public String getKeyColumnName()
	{
		return sqlKeyColumnName;
	}
	
	public String getLinkColumnName()
	{
		return sqlLinkColumnName;
	}

	public String getParentLinkColumnName()
	{
		return sqlParentLinkColumnName;
	}

	public IStringExpression getSqlSelectAllFrom()
	{
		return sqlSelectAllFrom;
	}

	public IStringExpression getSqlWhereClause()
	{
		return sqlWhereClause;
	}

	public List<DocumentQueryOrderBy> getOrderBys()
	{
		return orderBys;
	}

	public final IStringExpression buildSqlOrderBy(final List<DocumentQueryOrderBy> orderBys)
	{
		if (orderBys.isEmpty())
		{
			return IStringExpression.NULL;
		}

		final IStringExpression sqlOrderByFinal = orderBys
				.stream()
				.map(orderBy -> buildSqlOrderBy(orderBy))
				.filter(sql -> sql != null && !sql.isNullExpression())
				.collect(IStringExpression.collectJoining(", "));

		return sqlOrderByFinal;
	}

	public final IStringExpression buildSqlOrderBy(final DocumentQueryOrderBy orderBy)
	{
		final String fieldName = orderBy.getFieldName();
		final SqlDocumentFieldDataBindingDescriptor fieldBinding = getFieldByFieldName(fieldName);
		return fieldBinding.buildSqlOrderBy(orderBy.isAscending());
	}

	public final IStringExpression buildSqlFullOrderBy(final List<DocumentQueryOrderBy> orderBys)
	{
		if (orderBys.isEmpty())
		{
			return null;
		}

		final IStringExpression sqlOrderByFinal = orderBys
				.stream()
				.map(orderBy -> buildSqlFullOrderBy(orderBy))
				.filter(sql -> sql != null && !sql.isNullExpression())
				.collect(IStringExpression.collectJoining(", "));

		return sqlOrderByFinal;
	}

	private final IStringExpression buildSqlFullOrderBy(final DocumentQueryOrderBy orderBy)
	{
		final String fieldName = orderBy.getFieldName();
		final SqlDocumentFieldDataBindingDescriptor fieldBinding = getFieldByFieldName(fieldName);
		return fieldBinding.buildSqlFullOrderBy(orderBy.isAscending());
	}

	public String replaceTableNameWithTableAlias(final String sql)
	{
		if(sql == null || sql.isEmpty())
		{
			return sql;
		}
		
		final String sqlFixed = sql.replace(getTableName() + ".", getTableAlias() + ".");
		return sqlFixed;
	}

	public SqlDocumentFieldDataBindingDescriptor getFieldByFieldName(final String fieldName)
	{
		final SqlDocumentFieldDataBindingDescriptor field = _fieldsByFieldName.get(fieldName);
		if (field == null)
		{
			throw new IllegalArgumentException("No field found for fieldName=" + fieldName + " in " + this);
		}
		return field;
	}
	
	public SqlDocumentViewBinding getDocumentViewBinding()
	{
		return documentViewBinding;
	}
	
	public Optional<String> getSqlSelectVersionById()
	{
		return sqlSelectVersionById;
	}
	
	@Override
	public boolean isVersioningSupported()
	{
		return sqlSelectVersionById.isPresent();
	}

	public static final class Builder implements DocumentEntityDataBindingDescriptorBuilder
	{
		private SqlDocumentEntityDataBindingDescriptor _built = null;

		private DocumentsRepository documentsRepository;
		private String _sqlTableName;
		private String _tableAlias;
		private String _sqlLinkColumnName;
		private String _sqlParentLinkColumnName;
		private String _sqlWhereClause = null;
		private IStringExpression _sqlWhereClauseExpression;

		//
		private IStringExpression _sqlSelectAll; // will be built

		private final LinkedHashMap<String, SqlDocumentFieldDataBindingDescriptor> _fieldsByFieldName = new LinkedHashMap<>();
		private SqlDocumentFieldDataBindingDescriptor _keyField;
		
		private SqlDocumentViewBinding.Builder documentViewBinding = SqlDocumentViewBinding.builder();

		private Builder()
		{
			super();
		}

		@Override
		public SqlDocumentEntityDataBindingDescriptor getOrBuild()
		{
			if (_built == null)
			{
				_built = new SqlDocumentEntityDataBindingDescriptor(this);
			}
			return _built;
		}

		private final void assertNotBuilt()
		{
			if (_built != null)
			{
				throw new IllegalStateException("Already built: " + this);
			}
		}

		private IStringExpression getSqlSelectAll()
		{
			if (_sqlSelectAll == null)
			{
				buildSqlSelects();
			}
			return _sqlSelectAll;
		}

		/**
		 * @return SELECT ... FROM ....
		 */
		private void buildSqlSelects()
		{
			final Collection<SqlDocumentFieldDataBindingDescriptor> fields = getFieldsByFieldName().values();
			if (fields.isEmpty())
			{
				throw new IllegalStateException("No SQL fields found");
			}

			final List<IStringExpression> sqlSelectValuesList = new ArrayList<>(fields.size());
			final List<IStringExpression> sqlSelectDisplayNamesList = new ArrayList<>(fields.size());
			for (final SqlDocumentFieldDataBindingDescriptor sqlField : fields)
			{
				//
				// Value column
				final IStringExpression sqlSelectValue = sqlField.getSqlSelectValue();
				sqlSelectValuesList.add(sqlSelectValue);

				//
				// Display column, if any
				if (sqlField.isUsingDisplayColumn())
				{
					final IStringExpression sqlSelectDisplayValue = sqlField.getSqlSelectDisplayValue();
					sqlSelectDisplayNamesList.add(sqlSelectDisplayValue);
				}
			}

			//
			_sqlSelectAll = buildSqlSelect(sqlSelectValuesList, sqlSelectDisplayNamesList);
		}

		private final IStringExpression buildSqlSelect(final List<IStringExpression> sqlSelectValuesList, final List<IStringExpression> sqlSelectDisplayNamesList)
		{
			final String sqlTableName = getTableName();
			final String sqlTableAlias = getTableAlias();

			final IStringExpression sqlInnerExpr = IStringExpression.composer()
					.append("SELECT ")
					.append("\n ").appendAllJoining("\n, ", sqlSelectValuesList)
					.append("\n FROM ").append(sqlTableName)
					.wrap(AccessSqlStringExpression.wrapper(sqlTableName, IUserRolePermissions.SQL_FULLYQUALIFIED, IUserRolePermissions.SQL_RO)) // security
					.build();

			final CompositeStringExpression.Builder sqlBuilder = IStringExpression.composer()
					.append("SELECT ")
					.append("\n").append(sqlTableAlias).append(".*"); // Value fields

			// DisplayName fields
			if (!sqlSelectDisplayNamesList.isEmpty())
			{
				sqlBuilder.append("\n, ").appendAllJoining("\n, ", sqlSelectDisplayNamesList);
			}

			sqlBuilder.append("\n FROM (").append(sqlInnerExpr).append(") ").append(sqlTableAlias); // FROM

			return sqlBuilder.build();
		}

		private IStringExpression getSqlWhereClauseExpression()
		{
			if (_sqlWhereClauseExpression == null)
			{
				_sqlWhereClauseExpression = buildSqlWhereClause();
			}
			return _sqlWhereClauseExpression;
		}

		private IStringExpression buildSqlWhereClause()
		{
			if (Check.isEmpty(_sqlWhereClause, true))
			{
				return IStringExpression.NULL;
			}

			final String sqlWhereClausePrepared = _sqlWhereClause.trim()
					// NOTE: because current AD_Tab.WhereClause contain fully qualified TableNames, we shall replace them with our table alias
					// (e.g. "R_Request.SalesRep_ID=@#AD_User_ID@" shall become ""tableAlias.SalesRep_ID=@#AD_User_ID@"
					.replace(getTableName() + ".", getTableAlias() + ".") //
					;

			final IStringExpression sqlWhereClauseExpr = Services.get(IExpressionFactory.class).compileOrDefault(sqlWhereClausePrepared, IStringExpression.NULL, IStringExpression.class);
			return sqlWhereClauseExpr;
		}

		private List<DocumentQueryOrderBy> getOrderBysList()
		{
			// Build the ORDER BY from fields
			return getFieldsByFieldName()
					.values()
					.stream()
					.filter(field -> field.isDefaultOrderBy())
					.sorted(Comparator.comparing(SqlDocumentFieldDataBindingDescriptor::getDefaultOrderByPriority))
					.map(field -> DocumentQueryOrderBy.byFieldName(field.getFieldName(), field.isDefaultOrderByAscending()))
					.collect(GuavaCollectors.toImmutableList());
		}

		public Builder setDocumentsRepository(final DocumentsRepository documentsRepository)
		{
			assertNotBuilt();
			this.documentsRepository = documentsRepository;
			return this;
		}

		private DocumentsRepository getDocumentsRepository()
		{
			Check.assumeNotNull(documentsRepository, "Parameter documentsRepository is not null");
			return documentsRepository;
		}

		public Builder setTableName(final String sqlTableName)
		{
			assertNotBuilt();
			_sqlTableName = sqlTableName;
			documentViewBinding.setTableName(sqlTableName);
			return this;
		}

		public String getTableName()
		{
			return _sqlTableName;
		}

		private Builder setTableAlias(final String sqlTableAlias)
		{
			assertNotBuilt();
			_tableAlias = sqlTableAlias;
			documentViewBinding.setTableAlias(sqlTableAlias);
			return this;
		}

		public Builder setTableAliasFromDetailId(final DetailId detailId)
		{
			if (detailId == null)
			{
				setTableAlias(TABLEALIAS_Master);
			}
			else
			{
				setTableAlias(detailId.getTableAlias());
			}

			return this;
		}

		public String getTableAlias()
		{
			return _tableAlias;
		}
		
		public Builder setChildToParentLinkColumnNames(final IPair<String, String> childToParentLinkColumnNames)
		{
			assertNotBuilt();
			
			if(childToParentLinkColumnNames != null)
			{
				_sqlLinkColumnName = childToParentLinkColumnNames.getLeft();
				_sqlParentLinkColumnName = childToParentLinkColumnNames.getRight();
			}
			else
			{
				_sqlLinkColumnName = null;
				_sqlParentLinkColumnName = null;
			}
			return this;
		}

		public String getSqlLinkColumnName()
		{
			return _sqlLinkColumnName;
		}

		public String getSqlParentLinkColumnName()
		{
			return _sqlParentLinkColumnName;
		}

		public Builder setSqlWhereClause(final String sqlWhereClause)
		{
			assertNotBuilt();
			Check.assumeNotNull(sqlWhereClause, "Parameter sqlWhereClause is not null");
			_sqlWhereClause = sqlWhereClause;
			return this;
		}

		public Builder addField(final DocumentFieldDataBindingDescriptor field)
		{
			assertNotBuilt();
			Preconditions.checkNotNull(field, "field");

			final SqlDocumentFieldDataBindingDescriptor sqlField = SqlDocumentFieldDataBindingDescriptor.castOrNull(field);
			_fieldsByFieldName.put(sqlField.getFieldName(), sqlField);

			if (sqlField.isKeyColumn())
			{
				setKeyField(sqlField);
			}
			
			documentViewBinding.addField(sqlField);

			return this;
		}

		private Map<String, SqlDocumentFieldDataBindingDescriptor> getFieldsByFieldName()
		{
			return _fieldsByFieldName;
		}
		
		private Builder setKeyField(final SqlDocumentFieldDataBindingDescriptor keyField)
		{
			Check.assumeNull(_keyField, "More than one key field is not allowed: {}, {}", _keyField, keyField);
			this._keyField = keyField;
			return this;
		}

		private String getKeyColumnName()
		{
			return _keyField == null ? null : _keyField.getColumnName();
		}
		
		private SqlDocumentViewBinding buildDocumentViewBinding()
		{
			return documentViewBinding.build();
		}
		
		private Optional<String> getSqlSelectVersionById()
		{
			if(getFieldsByFieldName().get(FIELDNAME_Version) == null)
			{
				return Optional.empty();
			}
			
			final String keyColumnName = getKeyColumnName();
			if(keyColumnName == null)
			{
				return Optional.empty();
			}
			
			final String sql = "SELECT " + FIELDNAME_Version + " FROM " + getTableName() + " WHERE " + keyColumnName + "=?";
			return Optional.of(sql);
		}
	}
}
