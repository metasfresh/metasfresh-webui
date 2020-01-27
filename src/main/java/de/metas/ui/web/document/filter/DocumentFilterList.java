package de.metas.ui.web.document.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import de.metas.util.GuavaCollectors;
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

@EqualsAndHashCode
@ToString
public class DocumentFilterList
{
	public static final DocumentFilterList ofList(@Nullable final Collection<DocumentFilter> list)
	{
		return list != null && !list.isEmpty()
				? new DocumentFilterList(list)
				: EMPTY;
	}

	private static final DocumentFilterList ofMap(@NonNull final Map<String, DocumentFilter> filtersById)
	{
		return !filtersById.isEmpty() ? new DocumentFilterList(filtersById) : EMPTY;
	}

	public static final DocumentFilterList of(@NonNull final DocumentFilter filter)
	{
		return ofList(ImmutableList.of(filter));
	}

	public static final DocumentFilterList of(@NonNull final DocumentFilter... filters)
	{
		return ofList(Arrays.asList(filters));
	}

	public static Collector<DocumentFilter, ?, DocumentFilterList> toDocumentFilterList()
	{
		return GuavaCollectors.collectUsingListAccumulator(DocumentFilterList::ofList);
	}

	public static final DocumentFilterList EMPTY = new DocumentFilterList(ImmutableMap.of());

	private final ImmutableMap<String, DocumentFilter> filtersById;

	private DocumentFilterList(@NonNull final Collection<DocumentFilter> list)
	{
		filtersById = Maps.uniqueIndex(list, DocumentFilter::getFilterId);
	}

	private DocumentFilterList(@NonNull final Map<String, DocumentFilter> map)
	{
		filtersById = ImmutableMap.copyOf(map);
	}

	public static boolean equals(final DocumentFilterList list1, final DocumentFilterList list2)
	{
		return Objects.equals(list1, list2);
	}

	public boolean isEmpty()
	{
		return filtersById.isEmpty();
	}

	public ImmutableList<DocumentFilter> toList()
	{
		return ImmutableList.copyOf(filtersById.values());
	}

	public Stream<DocumentFilter> stream()
	{
		return filtersById.values().stream();
	}

	public DocumentFilterList mergeWith(@NonNull final DocumentFilterList other)
	{
		if (isEmpty())
		{
			return other;
		}
		else if (other.isEmpty())
		{
			return this;
		}
		else
		{
			final LinkedHashMap<String, DocumentFilter> filtersByIdNew = new LinkedHashMap<>(this.filtersById);
			filtersByIdNew.putAll(other.filtersById);

			return ofMap(filtersByIdNew);
		}
	}

	public DocumentFilterList mergeWith(@NonNull final DocumentFilter filter)
	{
		if (isEmpty())
		{
			return of(filter);
		}
		else
		{
			final LinkedHashMap<String, DocumentFilter> filtersByIdNew = new LinkedHashMap<>(this.filtersById);
			filtersByIdNew.put(filter.getFilterId(), filter);

			return ofMap(filtersByIdNew);
		}
	}

	public DocumentFilterList retainingOnly(@NonNull final Predicate<DocumentFilter> predicate)
	{
		if (isEmpty())
		{
			return this;
		}

		final LinkedHashMap<String, DocumentFilter> filtersByIdNew = new LinkedHashMap<>(this.filtersById.size());
		for (final Map.Entry<String, DocumentFilter> e : this.filtersById.entrySet())
		{
			final String filterId = e.getKey();
			final DocumentFilter filter = e.getValue();

			if (predicate.test(filter))
			{
				filtersByIdNew.put(filterId, filter);
			}
		}

		return this.filtersById.size() != filtersByIdNew.size()
				? ofMap(filtersByIdNew)
				: this;
	}

	public DocumentFilterList subtract(@NonNull final DocumentFilterList other)
	{
		return retainingOnly(filter -> !other.containsFilterById(filter.getFilterId()));
	}

	public Optional<DocumentFilter> getFilterById(@NonNull final String filterId)
	{
		final DocumentFilter filter = getFilterByIdOrNull(filterId);
		return Optional.ofNullable(filter);
	}

	public boolean containsFilterById(final String filterId)
	{
		return getFilterByIdOrNull(filterId) != null;
	}

	private DocumentFilter getFilterByIdOrNull(@NonNull final String filterId)
	{
		return filtersById.get(filterId);
	}

	public void forEach(@NonNull final Consumer<DocumentFilter> consumer)
	{
		filtersById.values().forEach(consumer);
	}

	public String getParamValueAsString(final String filterId, final String parameterName)
	{
		final DocumentFilter filter = getFilterByIdOrNull(filterId);
		if (filter == null)
		{
			return null;
		}

		return filter.getParameterValueAsString(parameterName);
	}

	public int getParamValueAsInt(final String filterId, final String parameterName, final int defaultValue)
	{
		final DocumentFilter filter = getFilterByIdOrNull(filterId);
		if (filter == null)
		{
			return defaultValue;
		}

		return filter.getParameterValueAsInt(parameterName, defaultValue);
	}

	public boolean getParamValueAsBoolean(final String filterId, final String parameterName, final boolean defaultValue)
	{
		final DocumentFilter filter = getFilterByIdOrNull(filterId);
		if (filter == null)
		{
			return defaultValue;
		}

		return filter.getParameterValueAsBoolean(parameterName, defaultValue);
	}
}