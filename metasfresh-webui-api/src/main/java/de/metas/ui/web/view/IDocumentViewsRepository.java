package de.metas.ui.web.view;

import java.util.List;
import java.util.Set;

import org.adempiere.util.lang.impl.TableRecordReference;

import de.metas.ui.web.view.json.JSONCreateDocumentViewRequest;

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

public interface IDocumentViewsRepository
{
	IDocumentViewSelection getView(String viewId);

	default <T extends IDocumentViewSelection> T getView(final String viewId, final Class<T> type)
	{
		@SuppressWarnings("unchecked")
		final T view = (T)getView(viewId);
		return view;
	}

	IDocumentViewSelection createView(JSONCreateDocumentViewRequest jsonRequest);

	void deleteView(String viewId);

	List<IDocumentViewSelection> getViews();

	/**
	 * Notify all views that given records was changed.
	 */
	void notifyRecordsChanged(Set<TableRecordReference> recordRefs);

}
