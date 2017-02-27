package de.metas.ui.web.handlingunits;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.adempiere.util.Check;

import com.google.common.base.MoreObjects;

import de.metas.handlingunits.model.X_M_HU;
import de.metas.ui.web.view.IDocumentView;
import de.metas.ui.web.view.IDocumentViewAttributes;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;

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

public final class HUDocumentView implements IDocumentView
{
	public static final HUDocumentView of(final IDocumentView delegate)
	{
		return new HUDocumentView(delegate);
	}

	public static final HUDocumentView cast(final IDocumentView document)
	{
		return (HUDocumentView)document;
	}

	private final IDocumentView delegate;

	private HUDocumentView(final IDocumentView delegate)
	{
		super();
		Check.assumeNotNull(delegate, "Parameter delegate is not null");
		Check.assumeNotNull(delegate.getType(), "type shall not be null for {}", delegate);
		this.delegate = delegate;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.addValue(delegate)
				.toString();
	}

	@Override
	public DocumentPath getDocumentPath()
	{
		return delegate.getDocumentPath();
	}

	@Override
	public DocumentId getDocumentId()
	{
		return delegate.getDocumentId();
	}

	/**
	 * @return {@link HUDocumentViewType}; never returns null.
	 */
	@Override
	public HUDocumentViewType getType()
	{
		return (HUDocumentViewType)delegate.getType();
	}

	@Override
	public boolean isProcessed()
	{
		return delegate.isProcessed();
	}

	@Override
	public String getIdFieldNameOrNull()
	{
		return delegate.getIdFieldNameOrNull();
	}

	@Override
	public Set<String> getFieldNames()
	{
		return delegate.getFieldNames();
	}

	@Override
	public Object getFieldValueAsJson(final String fieldName)
	{
		return delegate.getFieldValueAsJson(fieldName);
	}

	@Override
	public Map<String, Object> getFieldNameAndJsonValues()
	{
		return delegate.getFieldNameAndJsonValues();
	}

	@Override
	public boolean hasAttributes()
	{
		return delegate.hasAttributes();
	}

	@Override
	public IDocumentViewAttributes getAttributes()
	{
		return delegate.getAttributes();
	}

	@Override
	public List<HUDocumentView> getIncludedDocuments()
	{
		@SuppressWarnings("unchecked")
		final List<HUDocumentView> includedHUDocuments = (List<HUDocumentView>)(List<? extends IDocumentView>)delegate.getIncludedDocuments();
		return includedHUDocuments;
	}

	public int getM_HU_ID()
	{
		return (int)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_M_HU_ID);
	}

	public String getValue()
	{
		return (String)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_Value);
	}

	public String getHUStatus()
	{
		final JSONLookupValue jsonHUStatus = (JSONLookupValue)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_HUStatus);
		return jsonHUStatus == null ? null : jsonHUStatus.getKey();
	}

	public boolean isHUStatusPlanning()
	{
		return X_M_HU.HUSTATUS_Planning.equals(getHUStatus());
	}

	public boolean isHUStatusActive()
	{
		return X_M_HU.HUSTATUS_Active.equals(getHUStatus());
	}

	public boolean isPureHU()
	{
		return getType().isPureHU();
	}
	
	public boolean isCU()
	{
		return getType().isCU();
	}

	public String getSummary()
	{
		return getValue();
	}

	public int getM_Product_ID()
	{
		final JSONLookupValue productLV = (JSONLookupValue)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_M_Product_ID);
		return productLV == null ? -1 : productLV.getKeyAsInt();
	}
	
	public int getC_UOM_ID()
	{
		final JSONLookupValue uomLV = (JSONLookupValue)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_C_UOM_ID);
		return uomLV == null ? -1 : uomLV.getKeyAsInt();
	}
	
	public BigDecimal getQtyCU()
	{
		return (BigDecimal)delegate.getFieldValueAsJson(I_WEBUI_HU_View.COLUMNNAME_QtyCU);
	}


}
