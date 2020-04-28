package de.metas.ui.web.payment_allocation.process;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.adempiere.exceptions.AdempiereException;

import com.google.common.collect.ImmutableList;

import de.metas.banking.payment.paymentallocation.service.AllocationAmounts;
import de.metas.banking.payment.paymentallocation.service.PayableDocument;
import de.metas.banking.payment.paymentallocation.service.PaymentAllocationBuilder;
import de.metas.banking.payment.paymentallocation.service.PaymentAllocationBuilder.PayableRemainingOpenAmtPolicy;
import de.metas.banking.payment.paymentallocation.service.PaymentAllocationResult;
import de.metas.banking.payment.paymentallocation.service.PaymentDocument;
import de.metas.money.Money;
import de.metas.money.MoneyService;
import de.metas.ui.web.payment_allocation.InvoiceRow;
import de.metas.ui.web.payment_allocation.PaymentRow;
import de.metas.util.lang.CoalesceUtil;
import de.metas.util.time.SystemTime;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

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

public class PaymentsViewAllocateCommand
{
	private final MoneyService moneyService;

	private final PaymentRow paymentRow;
	private final List<InvoiceRow> invoiceRows;
	private final PayableRemainingOpenAmtPolicy payableRemainingOpenAmtPolicy;
	private final LocalDate dateTrx;

	@Builder
	private PaymentsViewAllocateCommand(
			@NonNull final MoneyService moneyService,
			@Nullable final PaymentRow paymentRow,
			@NonNull @Singular final ImmutableList<InvoiceRow> invoiceRows,
			@Nullable final PayableRemainingOpenAmtPolicy payableRemainingOpenAmtPolicy,
			@Nullable final LocalDate dateTrx)
	{
		this.moneyService = moneyService;
		this.paymentRow = paymentRow;
		this.invoiceRows = invoiceRows;
		this.payableRemainingOpenAmtPolicy = CoalesceUtil.coalesce(payableRemainingOpenAmtPolicy, PayableRemainingOpenAmtPolicy.DO_NOTHING);
		this.dateTrx = dateTrx != null ? dateTrx : SystemTime.asLocalDate();
	}

	public Optional<PaymentAllocationResult> dryRun()
	{
		final PaymentAllocationBuilder builder = preparePaymentAllocationBuilder();
		if (builder == null)
		{
			return Optional.empty();
		}

		final PaymentAllocationResult result = builder.dryRun().build();
		return Optional.of(result);
	}

	public PaymentAllocationResult run()
	{
		final PaymentAllocationBuilder builder = preparePaymentAllocationBuilder();
		if (builder == null)
		{
			throw new AdempiereException("Invalid allocation")
					.appendParametersToMessage()
					.setParameter("paymentRow", paymentRow)
					.setParameter("invoiceRows", invoiceRows)
					.setParameter("payableRemainingOpenAmtPolicy", payableRemainingOpenAmtPolicy);
		}
		return builder.build();
	}

	private PaymentAllocationBuilder preparePaymentAllocationBuilder()
	{
		if (paymentRow == null && invoiceRows.isEmpty())
		{
			return null;
		}

		final List<PaymentDocument> paymentDocuments = paymentRow != null
				? ImmutableList.of(toPaymentDocument(paymentRow))
				: ImmutableList.of();

		final ImmutableList<PayableDocument> invoiceDocuments = invoiceRows.stream()
				.map(this::toPayableDocument)
				.collect(ImmutableList.toImmutableList());

		return PaymentAllocationBuilder.newBuilder()
				.dateTrx(dateTrx)
				.dateAcct(dateTrx)
				.paymentDocuments(paymentDocuments)
				.payableDocuments(invoiceDocuments)
				.allowPartialAllocations(true)
				.payableRemainingOpenAmtPolicy(payableRemainingOpenAmtPolicy);
	}

	private PayableDocument toPayableDocument(final InvoiceRow row)
	{
		final Money openAmt = moneyService.toMoney(row.getOpenAmt());
		final Money discountAmt = moneyService.toMoney(row.getDiscountAmt());

		return PayableDocument.builder()
				.orgId(row.getOrgId())
				.invoiceId(row.getInvoiceId())
				.bpartnerId(row.getBPartnerId())
				.documentNo(row.getDocumentNo())
				.soTrx(row.getSoTrx())
				.creditMemo(row.isCreditMemo())
				.openAmt(openAmt)
				.amountsToAllocate(AllocationAmounts.builder()
						.payAmt(openAmt)
						.discountAmt(discountAmt)
						.build())
				.build();
	}

	private PaymentDocument toPaymentDocument(final PaymentRow row)
	{
		final Money openAmt = moneyService.toMoney(row.getOpenAmt());

		return PaymentDocument.builder()
				.orgId(row.getOrgId())
				.paymentId(row.getPaymentId())
				.bpartnerId(row.getBPartnerId())
				.documentNo(row.getDocumentNo())
				.paymentDirection(row.getPaymentDirection())
				.openAmt(openAmt)
				.amountToAllocate(openAmt)
				.build();
	}

}
