package de.metas.ui.web.document.filter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DisplayType;

import com.google.common.collect.ImmutableList;

import de.metas.ui.web.view.descriptor.SqlAndParams;
import de.metas.ui.web.window.datatypes.LookupValue;
import de.metas.ui.web.window.datatypes.json.DateTimeConverters;
import de.metas.util.Check;
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

@Getter
@EqualsAndHashCode // required for (ETag) caching
@ToString
public final class DocumentFilterParam
{
	public enum Operator
	{
		EQUAL, //
		NOT_EQUAL, //
		IN_ARRAY, //
		LIKE, //
		/** Like (case-insensitive) */
		LIKE_I, //
		NOT_LIKE, //
		/** Not Like (case-insensitive) */
		NOT_LIKE_I, //
		GREATER, //
		GREATER_OR_EQUAL, //
		LESS, //
		LESS_OR_EQUAL, //
		BETWEEN, //
		;

		public boolean isRangeOperator()
		{
			return this == BETWEEN;
		}
	}

	private final boolean joinAnd;
	private final String fieldName;
	private final Operator operator;
	private final Object value;
	private final Object valueTo;
	//
	private final SqlAndParams sqlWhereClause;

	public static Builder builder()
	{
		return new Builder();
	}

	public static DocumentFilterParam ofSqlWhereClause(final boolean joinAnd, final String sqlWhereClause)
	{
		return new DocumentFilterParam(joinAnd, SqlAndParams.of(sqlWhereClause));
	}

	public static DocumentFilterParam ofNameEqualsValue(
			@NonNull final String fieldName,
			@NonNull final Object value)
	{
		return ofNameOperatorValue(fieldName, Operator.EQUAL, value);
	}

	/**
	 * Shortcut to create an often-used kind of parameters.
	 */
	public static DocumentFilterParam ofNameOperatorValue(
			@NonNull final String fieldName,
			@NonNull final Operator operator,
			@NonNull final Object value)
	{
		return builder().setFieldName(fieldName).setOperator(operator).setValue(value).build();
	}

	private DocumentFilterParam(final Builder builder)
	{
		joinAnd = builder.joinAnd;

		fieldName = builder.fieldName;
		Check.assumeNotNull(fieldName, "Parameter fieldName is not null");

		operator = builder.operator;
		Check.assumeNotNull(operator, "Parameter operator is not null");

		value = builder.value;
		valueTo = builder.valueTo;

		sqlWhereClause = null;
	}

	/** Hardcoded SQL WHERE clause builder */
	private DocumentFilterParam(final boolean joinAnd, @NonNull final SqlAndParams sqlWhereClause)
	{
		this.joinAnd = joinAnd;

		fieldName = null;
		operator = null;
		value = null;
		valueTo = null;

		this.sqlWhereClause = sqlWhereClause;
	}

	public boolean isSqlFilter()
	{
		return getSqlWhereClause() != null;
	}

	public String getValueAsString()
	{
		return value != null ? value.toString() : null;
	}

	public int getValueAsInt(final int defaultValue)
	{
		final Integer valueInt = convertToInt(value);
		return valueInt != null ? valueInt : defaultValue;
	}

	public Boolean getValueAsBoolean(final Boolean defaultValue)
	{
		return DisplayType.toBoolean(value, defaultValue);
	}

	public LocalDate getValueAsLocalDateOr(final LocalDate defaultValue)
	{
		return value != null ? DateTimeConverters.fromObjectToLocalDate(value) : defaultValue;
	}

	public LocalDate getValueToAsLocalDateOr(final LocalDate defaultValue)
	{
		return valueTo != null ? DateTimeConverters.fromObjectToLocalDate(valueTo) : defaultValue;
	}

	public Instant getValueAsInstant()
	{
		return value != null ? DateTimeConverters.fromObjectToInstant(value) : null;
	}

	public Instant getValueToAsInstant()
	{
		return valueTo != null ? DateTimeConverters.fromObjectToInstant(valueTo) : null;
	}

	public Collection<?> getValueAsCollection()
	{
		if (value == null)
		{
			throw new AdempiereException("Cannot convert null value to Collection<?>");
		}
		else if (value instanceof Collection)
		{
			return (Collection<?>)value;
		}
		else
		{
			return ImmutableList.of(value);
			// throw new AdempiereException("Cannot convert value to Collection<?>: " + value + " (" + value.getClass() + ")");
		}
	}

	public <T> List<T> getValueAsList(@NonNull final Function<Object, T> itemConverter)
	{
		final Collection<?> valueAsCollection = getValueAsCollection();
		if (valueAsCollection == null)
		{
			throw new AdempiereException("Cannot convert null value to List<Integer>");
		}

		if (valueAsCollection.isEmpty())
		{
			return ImmutableList.of();
		}

		return valueAsCollection.stream()
				.map(itemConverter)
				.collect(ImmutableList.toImmutableList());
	}

	public List<Integer> getValueAsIntList()
	{
		return getValueAsList(itemObj -> convertToInt(itemObj));
	}

	private static Integer convertToInt(final Object itemObj)
	{
		if (itemObj == null)
		{
			// pass-through, even though it will produce an exception when the list will be converted to immutable list
			return null;
		}
		else if (itemObj instanceof Number)
		{
			return ((Number)itemObj).intValue();
		}
		else if (itemObj instanceof LookupValue)
		{
			return ((LookupValue)itemObj).getIdAsInt();
		}
		else
		{
			final String itemStr = itemObj.toString();
			return Integer.parseInt(itemStr);
		}
	}

	public <T extends RepoIdAware> T getValueAsRepoIdOrNull(final @NonNull IntFunction<T> repoIdMapper)
	{
		final int idInt = getValueAsInt(-1);
		if (idInt < 0)
		{
			return null;
		}
		return repoIdMapper.apply(idInt);
	}

	public LocalDate getValueAsLocalDate()
	{
		return DateTimeConverters.fromObjectToLocalDate(value);
	}

	//
	//
	// ------------------
	//
	//

	public static final class Builder
	{
		private boolean joinAnd = true;
		private String fieldName;
		private Operator operator = Operator.EQUAL;
		private Object value;
		private Object valueTo;

		private Builder()
		{
			super();
		}

		public DocumentFilterParam build()
		{
			return new DocumentFilterParam(this);
		}

		public Builder setJoinAnd(final boolean joinAnd)
		{
			this.joinAnd = joinAnd;
			return this;
		}

		public Builder setFieldName(final String fieldName)
		{
			this.fieldName = fieldName;
			return this;
		}

		public Builder setOperator(@NonNull final Operator operator)
		{
			this.operator = operator;
			return this;
		}

		public Builder setOperator()
		{
			operator = valueTo != null ? Operator.BETWEEN : Operator.EQUAL;
			return this;
		}

		public Builder setValue(final Object value)
		{
			this.value = value;
			return this;
		}

		public Builder setValueTo(final Object valueTo)
		{
			this.valueTo = valueTo;
			return this;
		}
	}
}
