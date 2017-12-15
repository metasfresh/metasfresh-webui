package de.metas.ui.web.material.cockpit.rowfactory;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.model.I_M_Product;
import org.compiere.model.I_S_Resource;
import org.compiere.model.X_S_Resource;
import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import de.metas.dimension.DimensionSpec;
import de.metas.dimension.DimensionSpecGroup;
import de.metas.dimension.IDimensionspecDAO;
import de.metas.fresh.model.I_Fresh_QtyOnHand_Line;
import de.metas.fresh.model.I_X_MRP_ProductInfo_Detail_MV;
import de.metas.ui.web.material.cockpit.MaterialCockpitRow;
import lombok.NonNull;
import lombok.Value;

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

@Service
public class MaterialCockpitRowFactory
{
	public static final String DIM_SPEC_INTERNAL_NAME = "MRP_Product_Info_ASI_Values";

	@Value
	@lombok.Builder
	public static class CreateRowsRequest
	{
		@NonNull
		Timestamp date;
		@NonNull
		List<I_M_Product> relevantProducts;
		@NonNull
		List<I_Fresh_QtyOnHand_Line> countings;
		@NonNull
		List<I_X_MRP_ProductInfo_Detail_MV> dataRecords;
	}

	public List<MaterialCockpitRow> createRows(@NonNull final CreateRowsRequest request)
	{
		final Map<MainRowBucketId, MainRowBucket> emptyRowBuckets = createEmptyRowBuckets(
				request.getRelevantProducts(),
				request.getDate());

		final DimensionSpec dimensionSpec = Services.get(IDimensionspecDAO.class).retrieveForInternalNameOrNull(DIM_SPEC_INTERNAL_NAME);
		Check.errorIf(dimensionSpec == null, "Unable to load DIM_Dimension_Spec record with InternalName={}", DIM_SPEC_INTERNAL_NAME);

		final Map<MainRowBucketId, MainRowBucket> result = new HashMap<>(emptyRowBuckets);

		for (final I_Fresh_QtyOnHand_Line counting : request.getCountings())
		{
			final MainRowBucketId mayRowBucketId = MainRowBucketId.createPlainInstance(
					counting.getM_Product_ID(),
					request.getDate());
			final MainRowBucket mainRowBucket = result.computeIfAbsent(mayRowBucketId, key -> MainRowBucket.create(key));
			mainRowBucket.addCounting(counting);
		}

		for (final I_X_MRP_ProductInfo_Detail_MV dbRow : request.getDataRecords())
		{
			final MainRowBucketId mayRowBucketId = MainRowBucketId.createInstanceForDataRecord(dbRow);
			final MainRowBucket mainRowBucket = result.computeIfAbsent(mayRowBucketId, key -> MainRowBucket.create(key));
			mainRowBucket.addDataRecord(dbRow, dimensionSpec);
		}

		return result.values()
				.stream()
				.map(MainRowBucket::createMainRowWithSubRows)
				.collect(ImmutableList.toImmutableList());
	}

	@VisibleForTesting
	Map<MainRowBucketId, MainRowBucket> createEmptyRowBuckets(
			@NonNull final List<I_M_Product> products,
			@NonNull final Timestamp timestamp)
	{
		final DimensionSpec dimensionSpec = Services.get(IDimensionspecDAO.class).retrieveForInternalNameOrNull(DIM_SPEC_INTERNAL_NAME);
		Check.errorIf(dimensionSpec == null, "Unable to load DIM_Dimension_Spec record with InternalName={}", DIM_SPEC_INTERNAL_NAME);

		final List<DimensionSpecGroup> groups = dimensionSpec.retrieveGroups();
		final List<I_S_Resource> plants = retrieveCountingPlants();

		final Builder<MainRowBucketId, MainRowBucket> result = ImmutableMap.builder();
		for (final I_M_Product product : products)
		{
			final MainRowBucketId key = MainRowBucketId.createPlainInstance(product.getM_Product_ID(), timestamp);
			final MainRowBucket mainRowBucket = MainRowBucket.create(key);

			for (final I_S_Resource plant : plants)
			{
				mainRowBucket.addEmptyCountingSubrowBucket(plant.getS_Resource_ID());
			}

			for (final DimensionSpecGroup group : groups)
			{
				mainRowBucket.addEmptyAttributesSubrowBucket(group);
			}
			result.put(key, mainRowBucket);

		}
		return result.build();
	}

	private List<I_S_Resource> retrieveCountingPlants()
	{
		return Services.get(IQueryBL.class).createQueryBuilder(I_S_Resource.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_S_Resource.COLUMNNAME_ManufacturingResourceType, X_S_Resource.MANUFACTURINGRESOURCETYPE_Plant)
				.create().list();
	}

}
