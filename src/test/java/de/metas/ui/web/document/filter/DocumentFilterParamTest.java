package de.metas.ui.web.document.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.metas.ui.web.document.filter.DocumentFilterParam.Operator;

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

public class DocumentFilterParamTest
{
	@Nested
	public class equalsTest
	{
		@Test
		public void testEquals()
		{
			final DocumentFilterParam param1 = DocumentFilterParam.builder()
					.setFieldName("param1")
					.setOperator(Operator.BETWEEN)
					.setValue("value1")
					.setValueTo("value2")
					.build();
			final DocumentFilterParam param2 = DocumentFilterParam.builder()
					.setFieldName("param1")
					.setOperator(Operator.BETWEEN)
					.setValue("value1")
					.setValueTo("value2")
					.build();

			assertThat(param1).isEqualTo(param2);
		}

		@Test
		public void testNotEquals()
		{
			final DocumentFilterParam param1 = DocumentFilterParam.builder()
					.setFieldName("param1")
					.setOperator(Operator.BETWEEN)
					.setValue("value1")
					.setValueTo("value2")
					.build();
			final DocumentFilterParam param2 = DocumentFilterParam.builder()
					.setFieldName("param1")
					.setOperator(Operator.BETWEEN)
					.setValue("value1")
					.setValueTo("value3")
					.build();

			assertThat(param1).isNotEqualTo(param2);
		}
	}
}
