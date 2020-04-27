package de.metas.ui.web.payment_allocation.process;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import org.adempiere.service.ClientId;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.metas.adempiere.model.I_C_Invoice;
import de.metas.banking.payment.paymentallocation.service.AllocationLineCandidate;
import de.metas.banking.payment.paymentallocation.service.AllocationLineCandidate.AllocationLineCandidateType;
import de.metas.banking.payment.paymentallocation.service.PaymentAllocationResult;
import de.metas.bpartner.BPartnerId;
import de.metas.currency.Amount;
import de.metas.currency.CurrencyCode;
import de.metas.currency.CurrencyRepository;
import de.metas.document.archive.model.I_C_BPartner;
import de.metas.i18n.TranslatableStrings;
import de.metas.invoice.InvoiceId;
import de.metas.lang.SOTrx;
import de.metas.money.MoneyService;
import de.metas.organization.ClientAndOrgId;
import de.metas.organization.OrgId;
import de.metas.payment.PaymentDirection;
import de.metas.payment.PaymentId;
import de.metas.ui.web.payment_allocation.InvoiceRow;
import de.metas.ui.web.payment_allocation.PaymentRow;
import de.metas.ui.web.window.datatypes.LookupValue.IntegerLookupValue;
import lombok.Builder;
import lombok.NonNull;

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

public class PaymentsViewAllocateCommandTest
{
	private static final OrgId orgId = OrgId.ofRepoId(1);

	private MoneyService moneyService;
	private BPartnerId bpartnerId;

	@BeforeEach
	public void beforeEach()
	{
		AdempiereTestHelper.get().init();

		moneyService = new MoneyService(new CurrencyRepository());

		bpartnerId = createBPartnerId();
	}

	private BPartnerId createBPartnerId()
	{
		final I_C_BPartner bpartnerRecord = newInstance(I_C_BPartner.class);
		saveRecord(bpartnerRecord);
		return BPartnerId.ofRepoId(bpartnerRecord.getC_BPartner_ID());
	}

	private Amount euro(final int amount)
	{
		return Amount.of(amount, CurrencyCode.EUR);
	}

	private TableRecordReference toRecordRef(final PaymentRow paymentRow)
	{
		return TableRecordReference.of(I_C_Payment.Table_Name, paymentRow.getPaymentId());
	}

	private TableRecordReference toRecordRef(final InvoiceRow invoiceRow)
	{
		return TableRecordReference.of(I_C_Invoice.Table_Name, invoiceRow.getInvoiceId());
	}

	@Builder(builderMethodName = "paymentRow", builderClassName = "PaymentRowBuilder")
	private PaymentRow createPaymentRow(
			@NonNull final PaymentDirection direction,
			@NonNull final Amount payAmt)
	{
		final I_C_Payment paymentRecord = newInstance(I_C_Payment.class);
		saveRecord(paymentRecord);
		final PaymentId paymentId = PaymentId.ofRepoId(paymentRecord.getC_Payment_ID());

		return PaymentRow.builder()
				.paymentId(paymentId)
				.clientAndOrgId(ClientAndOrgId.ofClientAndOrg(ClientId.METASFRESH, orgId))
				.documentNo("paymentNo_" + paymentId.getRepoId())
				.dateTrx(LocalDate.of(2020, Month.APRIL, 25))
				.bpartner(IntegerLookupValue.of(bpartnerId.getRepoId(), "BPartner"))
				.payAmt(payAmt)
				.openAmt(payAmt)
				.paymentDirection(direction)
				.build();
	}

	@Builder(builderMethodName = "invoiceRow", builderClassName = "InvoiceRowBuilder")
	private InvoiceRow createInvoiceRow(
			@NonNull final Amount openAmt,
			@NonNull final SOTrx soTrx,
			final boolean creditMemo)
	{
		final I_C_Invoice invoiceRecord = newInstance(I_C_Invoice.class);
		saveRecord(invoiceRecord);
		final InvoiceId invoiceId = InvoiceId.ofRepoId(invoiceRecord.getC_Invoice_ID());

		return InvoiceRow.builder()
				.invoiceId(invoiceId)
				.clientAndOrgId(ClientAndOrgId.ofClientAndOrg(ClientId.METASFRESH, orgId))
				.docTypeName(TranslatableStrings.anyLanguage("invoice doc type"))
				.documentNo("invoiceNo_" + invoiceId.getRepoId())
				.dateInvoiced(LocalDate.of(2020, Month.APRIL, 1))
				.bpartner(IntegerLookupValue.of(bpartnerId.getRepoId(), "BPartner"))
				.soTrx(soTrx)
				.creditMemo(creditMemo)
				.grandTotal(openAmt)
				.openAmt(openAmt)
				.discountAmt(Amount.zero(openAmt.getCurrencyCode()))
				.build();
	}

	@Test
	public void singleInvoice_to_singlePayment()
	{
		final PaymentRow paymentRow = paymentRow().direction(PaymentDirection.INBOUND).payAmt(euro(100)).build();
		final InvoiceRow invoiceRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(100)).build();

		final PaymentAllocationResult result = PaymentsViewAllocateCommand.builder()
				.moneyService(moneyService)
				.paymentRow(paymentRow)
				.invoiceRow(invoiceRow)
				.build()
				.run();

		assertThat(result.isOK()).isTrue();
		assertThat(result.getCandidates()).hasSize(1);
		assertThat(result.getCandidates().get(0))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToPayment)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(paymentRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("100"))
						.build());
	}

	@Test
	public void invoice_to_creditMemo()
	{
		final InvoiceRow invoiceRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(100)).build();
		final InvoiceRow creditMemoRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(-20)).creditMemo(true).build();

		final PaymentAllocationResult result = PaymentsViewAllocateCommand.builder()
				.moneyService(moneyService)
				.invoiceRow(invoiceRow)
				.invoiceRow(creditMemoRow)
				.build()
				.run();

		System.out.println(result);

		assertThat(result.isOK()).isTrue();
		assertThat(result.getCandidates()).hasSize(1);
		assertThat(result.getCandidates().get(0))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToCreditMemo)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(creditMemoRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("20"))
						.payableOverUnderAmt(new BigDecimal("80"))
						.build());
	}

	@Test
	public void invoice_creditMemo_and_payment()
	{
		final PaymentRow paymentRow = paymentRow().direction(PaymentDirection.INBOUND).payAmt(euro(80)).build();
		final InvoiceRow invoiceRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(100)).build();
		final InvoiceRow creditMemoRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(-20)).creditMemo(true).build();

		final PaymentAllocationResult result = PaymentsViewAllocateCommand.builder()
				.moneyService(moneyService)
				.paymentRow(paymentRow)
				.invoiceRow(invoiceRow)
				.invoiceRow(creditMemoRow)
				.build()
				.run();

		System.out.println(result);

		assertThat(result.isOK()).isTrue();
		assertThat(result.getCandidates()).hasSize(2);
		assertThat(result.getCandidates().get(0))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToCreditMemo)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(creditMemoRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("20"))
						.payableOverUnderAmt(new BigDecimal("80"))
						.build());
		assertThat(result.getCandidates().get(1))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToPayment)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(paymentRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("80"))
						.build());
	}

	@Test
	public void invoice_creditMemo_and_payment_partial()
	{
		final PaymentRow paymentRow = paymentRow().direction(PaymentDirection.INBOUND).payAmt(euro(200)).build();
		final InvoiceRow invoiceRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(100)).build();
		final InvoiceRow creditMemoRow = invoiceRow().soTrx(SOTrx.SALES).openAmt(euro(-20)).creditMemo(true).build();

		final PaymentAllocationResult result = PaymentsViewAllocateCommand.builder()
				.moneyService(moneyService)
				.paymentRow(paymentRow)
				.invoiceRow(invoiceRow)
				.invoiceRow(creditMemoRow)
				.build()
				.run();

		System.out.println(result);

		assertThat(result.isOK()).isTrue();
		assertThat(result.getCandidates()).hasSize(2);
		assertThat(result.getCandidates().get(0))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToCreditMemo)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(creditMemoRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("20"))
						.payableOverUnderAmt(new BigDecimal("80"))
						.build());
		assertThat(result.getCandidates().get(1))
				.isEqualToComparingFieldByField(AllocationLineCandidate.builder()
						.type(AllocationLineCandidateType.InvoiceToPayment)
						.bpartnerId(bpartnerId)
						.paymentDocumentRef(toRecordRef(paymentRow))
						.payableDocumentRef(toRecordRef(invoiceRow))
						.amount(new BigDecimal("80"))
						.paymentOverUnderAmt(new BigDecimal("120"))
						.build());
	}
}
