package de.metas.ui.web.material.cockpit;

import java.sql.Timestamp;
import java.util.List;

import org.adempiere.ad.dao.ICompositeQueryFilter;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.util.Services;
import org.compiere.model.I_M_Product;
import org.compiere.util.CCache;
import org.compiere.util.Env;
import org.springframework.stereotype.Repository;

import com.google.common.collect.ImmutableList;

import de.metas.fresh.model.I_Fresh_QtyOnHand;
import de.metas.fresh.model.I_Fresh_QtyOnHand_Line;
import de.metas.fresh.model.I_X_MRP_ProductInfo_Detail_MV;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.material.cockpit.rowfactory.MaterialCockpitRowFactory;
import de.metas.ui.web.material.cockpit.rowfactory.MaterialCockpitRowFactory.CreateRowsRequest;
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

@Repository
public class MaterialCockpitRowRepository
{
	private final transient CCache<Integer, List<I_M_Product>> orgIdToproducts = CCache.newCache(
			"MaterialCockpitRowRepository#" + I_M_Product.Table_Name,
			10, // initial size
			CCache.EXPIREMINUTES_Never);

	private final MaterialCockpitFilters materialCockpitFilters;

	private final MaterialCockpitRowFactory materialCockpitRowFactory;

	public MaterialCockpitRowRepository(
			@NonNull final MaterialCockpitFilters materialCockpitFilters,
			@NonNull final MaterialCockpitRowFactory materialCockpitRowFactory)
	{
		this.materialCockpitFilters = materialCockpitFilters;
		this.materialCockpitRowFactory = materialCockpitRowFactory;
	}

	public List<MaterialCockpitRow> retrieveRows(@NonNull final List<DocumentFilter> filters)
	{
		final Timestamp date = materialCockpitFilters.extractDateOrNull(filters);
		if (date == null)
		{
			return ImmutableList.of();
		}
		final List<I_X_MRP_ProductInfo_Detail_MV> productInfoDetailRecords = materialCockpitFilters
				.createQuery(filters)
				.list();

		final List<I_Fresh_QtyOnHand_Line> countings = Services.get(IQueryBL.class).createQueryBuilder(I_Fresh_QtyOnHand.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_Fresh_QtyOnHand.COLUMN_Processed, true)
				.addEqualsFilter(I_Fresh_QtyOnHand.COLUMN_DateDoc, date)
				.andCollectChildren(I_Fresh_QtyOnHand_Line.COLUMN_Fresh_QtyOnHand_ID, I_Fresh_QtyOnHand_Line.class)
				.addOnlyActiveRecordsFilter()
				.create()
				.list();

		final CreateRowsRequest request = CreateRowsRequest.builder()
				.date(date)
				.relevantProducts(retrieveRelevantProducts(filters))
				.dataRecords(productInfoDetailRecords)
				.countings(countings)
				.build();
		return materialCockpitRowFactory.createRows(request);
	}

	private List<I_M_Product> retrieveRelevantProducts(@NonNull final List<DocumentFilter> filters)
	{
		final int orgId = Env.getAD_Org_ID(Env.getCtx());
		final List<I_M_Product> allProducts = orgIdToproducts
				.getOrLoad(orgId, () -> retrieveAllProducts(orgId));

		return allProducts.stream()
				.filter(product -> materialCockpitFilters.doesProductMatchFilters(product, filters))
				.collect(ImmutableList.toImmutableList());
	}

	private List<I_M_Product> retrieveAllProducts(final int adOrgId)
	{
		final IQueryBL queryBL = Services.get(IQueryBL.class);
		final ICompositeQueryFilter<I_M_Product> relevantProductFilter = queryBL.createCompositeQueryFilter(I_M_Product.class)
				.setJoinOr()
				.addEqualsFilter(I_M_Product.COLUMN_IsSold, true)
				.addEqualsFilter(I_M_Product.COLUMN_IsPurchased, true)
				.addEqualsFilter(I_M_Product.COLUMN_IsStocked, true);

		final List<I_M_Product> products = queryBL.createQueryBuilder(I_M_Product.class)
				.addOnlyActiveRecordsFilter()
				.addInArrayFilter(I_M_Product.COLUMN_AD_Org_ID, adOrgId, 0)
				.filter(relevantProductFilter)
				.create()
				.list();
		return products;
	}
}
