package de.metas.ui.web.material.cockpit;

import static org.adempiere.model.InterfaceWrapperHelper.load;
import static org.adempiere.model.InterfaceWrapperHelper.loadOutOfTrx;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.adempiere.util.Check;
import org.compiere.model.I_S_Resource;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import de.metas.adempiere.model.I_M_Product;
import de.metas.dimension.DimensionSpecGroup;
import de.metas.fresh.model.I_X_MRP_ProductInfo_V;
import de.metas.ui.web.view.IViewRow;
import de.metas.ui.web.view.IViewRowType;
import de.metas.ui.web.view.ViewRow.DefaultRowType;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn;
import de.metas.ui.web.view.descriptor.annotation.ViewColumn.ViewColumnLayout;
import de.metas.ui.web.view.descriptor.annotation.ViewColumnHelper;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import lombok.Singular;

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

public class MaterialCockpitRow implements IViewRow
{

	private static final String SEPARATOR = "-";
	private static final Joiner DOCUMENT_ID_JOINER = Joiner.on(SEPARATOR).skipNulls();

	@ViewColumn(widgetType = DocumentFieldWidgetType.Date, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_DateGeneral, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 5) })
	private final Timestamp date;

	private final int productId;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Text, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_Value, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 10) })
	private final String productValue;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Text, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_ProductName, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 20) })
	private final String productName;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Text, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_M_Product_Category_ID, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 30) })
	private final String productCategoryOrSubRowName;

	// Zusage Lieferant
	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_PMM_QtyPromised_OnDate, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 100) })
	private final BigDecimal pmmQtyPromised;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_QtyReserved_OnDate, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyReserved;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_QtyOrdered_OnDate, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyOrdered;

	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_QtyMaterialentnahme, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyMaterialentnahme;

	// MRP MEnge
	// TODO also fill this column
	// @ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
	// captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_Fresh_QtyMRP, //
	// layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyMrp;

	// Zaehlbestand
	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_Fresh_QtyOnHand_OnDate, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyOnHand;

	// zusagbar Zaehlbestand
	@ViewColumn(widgetType = DocumentFieldWidgetType.Quantity, //
			captionKey = I_X_MRP_ProductInfo_V.COLUMNNAME_Fresh_QtyPromised, //
			layouts = { @ViewColumnLayout(when = JSONViewDataType.grid, seqNo = 110) })
	private final BigDecimal qtyPromised;

	private final DocumentId documentId;

	private final DocumentPath documentPath;

	private final List<MaterialCockpitRow> includedRows;

	private final IViewRowType rowType;

	private transient ImmutableMap<String, Object> _fieldNameAndJsonValues;

	@lombok.Builder(builderClassName = "MainRowBuilder", builderMethodName = "mainRowBuilder")
	private MaterialCockpitRow(
			@Singular final List<MaterialCockpitRow> includedRows)
	{
		Check.errorIf(includedRows.isEmpty(), "The given includedRows may not be empty");

		this.rowType = DefaultRowType.Row;

		this.date = extractDate(includedRows);
		this.productId = extractProductId(includedRows);

		this.documentId = DocumentId.of(DOCUMENT_ID_JOINER.join(
				date,
				productId));

		this.documentPath = DocumentPath.rootDocumentPath(
				MaterialCockpitConstants.WINDOWID_MaterialCockpitView,
				documentId);

		final I_M_Product product = load(productId, I_M_Product.class);
		this.productValue = product.getValue();
		this.productName = product.getName();
		this.productCategoryOrSubRowName = product.getM_Product_Category().getName();

		this.includedRows = includedRows;

		// the qtys and also "date" is actually aggregated from our includedRows
		BigDecimal temporaryPmmQtyPromised = null;
		BigDecimal temporaryQtyReserved = null;
		BigDecimal temporaryQtyOrdered = null;
		BigDecimal temporaryQtyMaterialentnahme = null;
		BigDecimal temporaryQtyMrp = null;
		BigDecimal temporaryQtyOnHand = null;
		BigDecimal temporaryQtyPromised = null;

		for (final MaterialCockpitRow subRow : includedRows)
		{
			temporaryPmmQtyPromised = addNumberOrNull(temporaryPmmQtyPromised, subRow.pmmQtyPromised);
			temporaryQtyReserved = addNumberOrNull(temporaryQtyReserved, subRow.qtyReserved);
			temporaryQtyOrdered = addNumberOrNull(temporaryQtyOrdered, subRow.qtyOrdered);
			temporaryQtyMaterialentnahme = addNumberOrNull(temporaryQtyMaterialentnahme, subRow.qtyMaterialentnahme);
			temporaryQtyMrp = addNumberOrNull(temporaryQtyMrp, subRow.qtyMrp);
			temporaryQtyOnHand = addNumberOrNull(temporaryQtyOnHand, subRow.qtyOnHand);
			temporaryQtyPromised = addNumberOrNull(temporaryQtyPromised, subRow.qtyPromised);
		}
		this.pmmQtyPromised = temporaryPmmQtyPromised;
		this.qtyReserved = temporaryQtyReserved;
		this.qtyOrdered = temporaryQtyOrdered;
		this.qtyMaterialentnahme = temporaryQtyMaterialentnahme;
		this.qtyMrp = temporaryQtyMrp;
		this.qtyOnHand = temporaryQtyOnHand;
		this.qtyPromised = temporaryQtyPromised;
	}

	private static BigDecimal addNumberOrNull(BigDecimal numberOrNull, BigDecimal augentOrNull)
	{
		if (numberOrNull == null && augentOrNull == null)
		{
			return null;
		}

		return Util.coalesce(numberOrNull, BigDecimal.ZERO).add(Util.coalesce(augentOrNull, BigDecimal.ZERO));
	}

	private static Timestamp extractDate(final List<MaterialCockpitRow> includedRows)
	{
		final List<Timestamp> dates = includedRows.stream().map(row -> row.date).distinct().collect(ImmutableList.toImmutableList());
		Check.errorIf(dates.size() > 1, "The given includedRows have different dates={}; includedRows={}", dates, includedRows);

		return dates.get(0);
	}

	private static int extractProductId(final List<MaterialCockpitRow> includedRows)
	{
		final List<Integer> productIds = includedRows.stream().map(row -> row.productId).distinct().collect(ImmutableList.toImmutableList());
		Check.errorIf(productIds.size() > 1, "The given includedRows have different productIds={}; includedRows={}", productIds, includedRows);

		return productIds.get(0);
	}

	@lombok.Builder(builderClassName = "AttributeSubRowBuilder", builderMethodName = "attributeSubRowBuilder")
	private MaterialCockpitRow(
			final int productId,
			final Timestamp date,
			final DimensionSpecGroup dimensionGroup,
			final BigDecimal pmmQtyPromised,
			final BigDecimal qtyReserved,
			final BigDecimal qtyOrdered,
			final BigDecimal qtyMaterialentnahme,
			final BigDecimal qtyMrp,
			final BigDecimal qtyPromised)
	{
		this.rowType = DefaultRowType.Line;

		final String dimensionGroupName = dimensionGroup.getGroupName().translate(Env.getAD_Language());

		this.documentId = DocumentId.of(DOCUMENT_ID_JOINER.join(
				date,
				productId,
				dimensionGroupName));

		this.documentPath = DocumentPath.rootDocumentPath(
				MaterialCockpitConstants.WINDOWID_MaterialCockpitView,
				documentId);

		final I_M_Product product = load(productId, I_M_Product.class);
		this.productValue = product.getValue();
		this.productName = product.getName();
		this.productCategoryOrSubRowName = dimensionGroupName;

		this.productId = productId;
		this.date = date;

		this.includedRows = ImmutableList.of();

		this.pmmQtyPromised = Util.coalesce(pmmQtyPromised, BigDecimal.ZERO);
		this.qtyReserved = Util.coalesce(qtyReserved, BigDecimal.ZERO);
		this.qtyOrdered = Util.coalesce(qtyOrdered, BigDecimal.ZERO);
		this.qtyMaterialentnahme = Util.coalesce(qtyMaterialentnahme, BigDecimal.ZERO);
		this.qtyMrp = Util.coalesce(qtyMrp, BigDecimal.ZERO);
		this.qtyOnHand = null;
		this.qtyPromised = Util.coalesce(qtyPromised, BigDecimal.ZERO);
	}

	@lombok.Builder(builderClassName = "CountingSubRowBuilder", builderMethodName = "countingSubRowBuilder")
	private MaterialCockpitRow(
			final int productId,
			final Timestamp date,
			final int plantId,
			final BigDecimal qtyOnHand)
	{
		this.rowType = DefaultRowType.Line;

		final I_S_Resource plant = loadOutOfTrx(plantId, I_S_Resource.class);
		final String plantName = plant.getName();

		this.documentId = DocumentId.of(DOCUMENT_ID_JOINER.join(
				date,
				productId,
				plantName));

		this.documentPath = DocumentPath.rootDocumentPath(
				MaterialCockpitConstants.WINDOWID_MaterialCockpitView,
				documentId);

		final I_M_Product product = load(productId, I_M_Product.class);
		this.productValue = product.getValue();
		this.productName = product.getName();
		this.productCategoryOrSubRowName = plantName;

		this.productId = productId;
		this.date = date;

		this.includedRows = ImmutableList.of();

		this.pmmQtyPromised = null;
		this.qtyReserved = null;
		this.qtyOrdered = null;
		this.qtyMaterialentnahme = null;
		this.qtyMrp = null;
		this.qtyOnHand = Util.coalesce(qtyOnHand, BigDecimal.ZERO);
		this.qtyPromised = null;
	}

	@Override
	public List<? extends IViewRow> getIncludedRows()
	{
		return includedRows;
	}

	@Override
	public DocumentId getId()
	{
		return documentId;
	}

	@Override
	public IViewRowType getType()
	{
		return rowType;
	}

	@Override
	public DocumentPath getDocumentPath()
	{
		return documentPath;
	}

	/**
	 * Return false, because with true, all rows are "grayed" out. This does not mean that the rows are editable.
	 */
	@Override
	public boolean isProcessed()
	{
		return false;
	}

	@Override
	public Map<String, Object> getFieldNameAndJsonValues()
	{
		if (_fieldNameAndJsonValues == null)
		{
			_fieldNameAndJsonValues = ViewColumnHelper.extractJsonMap(this);
		}
		return _fieldNameAndJsonValues;
	}

}
