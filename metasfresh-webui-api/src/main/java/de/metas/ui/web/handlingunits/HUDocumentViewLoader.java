package de.metas.ui.web.handlingunits;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.ad.service.IADReferenceDAO;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.util.GuavaCollectors;
import org.adempiere.util.Services;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Product;
import org.compiere.util.Env;

import de.metas.handlingunits.IHUDisplayNameBuilder;
import de.metas.handlingunits.IHandlingUnitsBL;
import de.metas.handlingunits.IHandlingUnitsDAO;
import de.metas.handlingunits.exceptions.HUException;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_Storage;
import de.metas.handlingunits.model.X_M_HU;
import de.metas.handlingunits.model.X_M_HU_PI_Version;
import de.metas.handlingunits.storage.IHUProductStorage;
import de.metas.handlingunits.storage.IHUStorage;
import de.metas.inoutcandidate.model.I_M_ReceiptSchedule;
import de.metas.printing.esb.base.util.Check;
import de.metas.ui.web.view.DocumentView;
import de.metas.ui.web.view.DocumentViewAttributesProviderFactory;
import de.metas.ui.web.view.IDocumentViewAttributesProvider;
import de.metas.ui.web.view.json.JSONCreateDocumentViewRequest;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentType;
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

public class HUDocumentViewLoader
{
	public static final HUDocumentViewLoader of(final JSONCreateDocumentViewRequest request, final String referencingTableName)
	{
		return new HUDocumentViewLoader(request, referencingTableName);
	}

	private final int adWindowId;
	private final String referencingTableName;
	private final CopyOnWriteArraySet<Integer> huIds = new CopyOnWriteArraySet<>();

	private final IDocumentViewAttributesProvider _attributesProvider;

	private HUDocumentViewLoader(final JSONCreateDocumentViewRequest request, final String referencingTableName)
	{
		super();

		adWindowId = request.getAD_Window_ID();
		this.referencingTableName = referencingTableName;

		final Set<Integer> filterOnlyIds = request.getFilterOnlyIds();
		if (filterOnlyIds != null && !filterOnlyIds.isEmpty())
		{
			huIds.addAll(filterOnlyIds);
		}

		if (huIds.isEmpty())
		{
			throw new IllegalArgumentException("No filters specified for " + request);
		}

		_attributesProvider = DocumentViewAttributesProviderFactory.instance.createProviderOrNull(DocumentType.Window, adWindowId);
	}

	public IDocumentViewAttributesProvider getAttributesProvider()
	{
		return _attributesProvider;
	}

	public void addHUs(final Collection<I_M_HU> husToAdd)
	{
		final Set<Integer> huIdsToAdd = husToAdd.stream()
				.map(I_M_HU::getM_HU_ID)
				.collect(GuavaCollectors.toImmutableSet());

		this.huIds.addAll(huIdsToAdd);
	}

	public List<HUDocumentView> retrieveDocumentViews()
	{
		return retrieveTopLevelHUs(huIds)
				.stream()
				.map(hu -> createDocumentView(hu))
				.collect(GuavaCollectors.toImmutableList());
	}

	private static List<I_M_HU> retrieveTopLevelHUs(final Collection<Integer> filterOnlyIds)
	{
		final IQueryBuilder<I_M_HU> queryBuilder = Services.get(IHandlingUnitsDAO.class)
				.createHUQueryBuilder()
				.setContext(Env.getCtx(), ITrx.TRXNAME_None)
				.setOnlyTopLevelHUs()
				.createQueryBuilder();

		if (filterOnlyIds != null && !filterOnlyIds.isEmpty())
		{
			queryBuilder.addInArrayFilter(I_M_HU.COLUMN_M_HU_ID, filterOnlyIds);
		}

		return queryBuilder
				.create()
				.list();
	}

	private HUDocumentView createDocumentView(final I_M_HU hu)
	{
		final boolean aggregateHU = Services.get(IHandlingUnitsBL.class).isAggregateHU(hu);

		final String huUnitTypeCode = hu.getM_HU_PI_Version().getHU_UnitType();
		final HUDocumentViewType huRecordType;
		if (aggregateHU)
		{
			huRecordType = HUDocumentViewType.TU;
		}
		else
		{
			huRecordType = HUDocumentViewType.ofHU_UnitType(huUnitTypeCode);
		}
		final String huUnitTypeDisplayName = huRecordType.getName();
		final JSONLookupValue huUnitTypeLookupValue = JSONLookupValue.of(huUnitTypeCode, huUnitTypeDisplayName);

		final JSONLookupValue huStatus = createHUStatusLookupValue(hu);
		final boolean processed = extractProcessed(hu);

		final DocumentView.Builder huViewRecord = DocumentView.builder(adWindowId)
				.setIdFieldName(I_WEBUI_HU_View.COLUMNNAME_M_HU_ID)
				.setType(huRecordType)
				.setAttributesProvider(getAttributesProvider())
				.setProcessed(processed)
				//
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_M_HU_ID, hu.getM_HU_ID())
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_Value, hu.getValue())
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_HU_UnitType, huUnitTypeLookupValue)
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_HUStatus, huStatus)
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_PackingInfo, extractPackingInfo(hu, huRecordType));

		//
		// Product/UOM/Qty if there is only one product stored
		final IHUProductStorage singleProductStorage = getSingleProductStorage(hu);
		if (singleProductStorage != null)
		{
			huViewRecord
					.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_M_Product_ID, createProductLookupValue(singleProductStorage.getM_Product()))
					.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_C_UOM_ID, createUOMLookupValue(singleProductStorage.getC_UOM()))
					.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_QtyCU, singleProductStorage.getQty());
		}

		//
		// Included HUs
		if (aggregateHU)
		{
			streamProductStorageDocumentViews(hu, processed)
					.forEach(huViewRecord::addIncludedDocument);
		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_LoadLogistiqueUnit.equals(huUnitTypeCode))
		{
			Services.get(IHandlingUnitsDAO.class).retrieveIncludedHUs(hu)
					.stream()
					.map(includedHU -> createDocumentView(includedHU))
					.forEach(huViewRecord::addIncludedDocument);
		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_TransportUnit.equals(huUnitTypeCode))
		{
			streamProductStorageDocumentViews(hu, processed)
					.forEach(huViewRecord::addIncludedDocument);
		}
		else if (X_M_HU_PI_Version.HU_UNITTYPE_VirtualPI.equals(huUnitTypeCode))
		{
			// do nothing
		}
		else
		{
			throw new HUException("Unknown HU_UnitType=" + huUnitTypeCode + " for " + hu);
		}

		return HUDocumentView.of(huViewRecord.build());
	}

	private static final String extractPackingInfo(final I_M_HU hu, final HUDocumentViewType huUnitType)
	{
		if (!huUnitType.isPureHU())
		{
			return "";
		}
		if (huUnitType == HUDocumentViewType.VHU)
		{
			return "";
		}

		final IHUDisplayNameBuilder helper = Services.get(IHandlingUnitsBL.class).buildDisplayName(hu)
				.setShowIncludedHUCount(true);

		final StringBuilder packingInfo = new StringBuilder();

		final String piName = helper.getPIName();
		packingInfo.append(Check.isEmpty(piName, true) ? "?" : piName);

		final int includedHUsCount = helper.getIncludedHUsCount();
		if (includedHUsCount > 0)
		{
			packingInfo.append(" x ").append(includedHUsCount);
		}

		return packingInfo.toString();
	}

	private final boolean extractProcessed(final I_M_HU hu)
	{
		//
		// Receipt schedule => consider the HU as processed if is not Planning (FIXME HARDCODED)
		if (I_M_ReceiptSchedule.Table_Name.equals(referencingTableName))
		{
			return !X_M_HU.HUSTATUS_Planning.equals(hu.getHUStatus());
		}
		else
		{
			return false;
		}
	}

	private Stream<HUDocumentView> streamProductStorageDocumentViews(final I_M_HU hu, final boolean processed)
	{
		return Services.get(IHandlingUnitsBL.class).getStorageFactory()
				.getStorage(hu)
				.getProductStorages()
				.stream()
				.map(huStorage -> createDocumentView(huStorage, processed));
	}

	private IHUProductStorage getSingleProductStorage(final I_M_HU hu)
	{
		final IHUStorage huStorage = Services.get(IHandlingUnitsBL.class).getStorageFactory()
				.getStorage(hu);

		final I_M_Product product = huStorage.getSingleProductOrNull();
		if (product == null)
		{
			return null;
		}

		final IHUProductStorage productStorage = huStorage.getProductStorage(product);
		return productStorage;
	}

	private HUDocumentView createDocumentView(final IHUProductStorage huStorage, final boolean processed)
	{
		final I_M_HU hu = huStorage.getM_HU();
		final I_M_Product product = huStorage.getM_Product();

		final JSONLookupValue huUnitTypeLookupValue = JSONLookupValue.of(X_M_HU_PI_Version.HU_UNITTYPE_VirtualPI, "CU");
		final JSONLookupValue huStatus = createHUStatusLookupValue(hu);

		final DocumentView storageDocument = DocumentView.builder(adWindowId)
				.setDocumentId(DocumentId.ofString(I_M_HU_Storage.Table_Name + "_" + hu.getM_HU_ID() + "_" + product.getM_Product_ID()))
				.setIdFieldName(null) // N/A
				.setType(HUDocumentViewType.HUStorage)
				.setProcessed(processed)
				//
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_M_HU_ID, hu.getM_HU_ID())
				// .putFieldValue(I_WEBUI_HU_View.COLUMNNAME_Value, hu.getValue()) // NOTE: don't show value on storage level
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_HU_UnitType, huUnitTypeLookupValue)
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_HUStatus, huStatus)
				//
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_M_Product_ID, createProductLookupValue(product))
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_C_UOM_ID, createUOMLookupValue(huStorage.getC_UOM()))
				.putFieldValue(I_WEBUI_HU_View.COLUMNNAME_QtyCU, huStorage.getQty())
				//
				.build();
		return HUDocumentView.of(storageDocument);
	}

	private static JSONLookupValue createHUStatusLookupValue(final I_M_HU hu)
	{
		final String huStatusKey = hu.getHUStatus();
		final String huStatusDisplayName = Services.get(IADReferenceDAO.class).retriveListName(Env.getCtx(), I_WEBUI_HU_View.HUSTATUS_AD_Reference_ID, huStatusKey);
		return JSONLookupValue.of(huStatusKey, huStatusDisplayName);
	}

	private static JSONLookupValue createProductLookupValue(final I_M_Product product)
	{
		if (product == null)
		{
			return null;
		}

		final String displayName = product.getValue() + "_" + product.getName();
		return JSONLookupValue.of(product.getM_Product_ID(), displayName);
	}

	private static JSONLookupValue createUOMLookupValue(final I_C_UOM uom)
	{
		if (uom == null)
		{
			return null;
		}

		return JSONLookupValue.of(uom.getC_UOM_ID(), uom.getUOMSymbol());
	}

}
