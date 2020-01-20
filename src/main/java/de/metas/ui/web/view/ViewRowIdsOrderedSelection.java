package de.metas.ui.web.view;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.model.DocumentQueryOrderBy;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

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

@Value
public final class ViewRowIdsOrderedSelection
{
	ViewId viewId;
	long size;
	ImmutableList<DocumentQueryOrderBy> orderBys;

	int queryLimit;
	boolean queryLimitHit;

	@Builder(toBuilder = true)
	private ViewRowIdsOrderedSelection(
			@NonNull ViewId viewId,
			long size,
			@Nullable List<DocumentQueryOrderBy> orderBys,
			int queryLimit)
	{
		this.viewId = viewId;
		this.size = size;
		this.orderBys = orderBys == null ? ImmutableList.of() : ImmutableList.copyOf(orderBys);
		this.queryLimit = queryLimit;

		this.queryLimitHit = queryLimit > 0
				&& size > 0
				&& size >= queryLimit;
	}

	public WindowId getWindowId()
	{
		return getViewId().getWindowId();
	}

	public String getSelectionId()
	{
		return getViewId().getViewId();
	}

	public ViewRowIdsOrderedSelection withSize(final int size)
	{
		return this.size == size
				? this
				: toBuilder().size(size).build();
	}
}
