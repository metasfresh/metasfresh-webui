/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2019 metas GmbH
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

package de.metas.banking.process;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.metas.allocation.api.IAllocationDAO;
import de.metas.banking.api.BankAccountId;
import de.metas.banking.interfaces.I_C_BankStatementLine_Ref;
import de.metas.banking.model.I_C_BankStatement;
import de.metas.banking.model.I_C_BankStatementLine;
import de.metas.banking.payment.IBankStatmentPaymentBL;
import de.metas.banking.service.IBankStatementDAO;
import de.metas.document.engine.DocStatus;
import de.metas.i18n.IMsgBL;
import de.metas.invoice.InvoiceId;
import de.metas.payment.PaymentId;
import de.metas.payment.TenderType;
import de.metas.payment.api.IPaymentBL;
import de.metas.payment.api.IPaymentDAO;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.ui.web.process.descriptor.ProcessParamLookupValuesProvider;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementFieldDescriptor;
import de.metas.ui.web.window.descriptor.LookupDescriptor;
import de.metas.ui.web.window.descriptor.sql.SqlLookupDescriptor;
import de.metas.ui.web.window.model.lookup.LookupDataSourceFactory;
import de.metas.util.Services;
import de.metas.util.time.SystemTime;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.invoice.service.IInvoiceDAO;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.I_C_Payment;
import org.compiere.util.DisplayType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class C_BankStatement_AllocateInvoices extends JavaProcess implements IProcessPrecondition
{

	public static final String C_INVOICE_1_ID_PARAM_NAME = "C_Invoice_1_ID";
	@Param(parameterName = C_INVOICE_1_ID_PARAM_NAME, mandatory = true)
	private InvoiceId c_invoice_1_id;

	public static final String C_INVOICE_2_ID_PARAM_NAME = "C_Invoice_2_ID";
	@Param(parameterName = C_INVOICE_2_ID_PARAM_NAME)
	private InvoiceId c_invoice_2_id;

	public static final String C_INVOICE_3_ID_PARAM_NAME = "C_Invoice_3_ID";
	@Param(parameterName = C_INVOICE_3_ID_PARAM_NAME)
	private InvoiceId c_invoice_3_id;

	public static final String C_INVOICE_4_ID_PARAM_NAME = "C_Invoice_4_ID";
	@Param(parameterName = C_INVOICE_4_ID_PARAM_NAME)
	private InvoiceId c_invoice_4_id;

	public static final String C_INVOICE_5_ID_PARAM_NAME = "C_Invoice_5_ID";
	@Param(parameterName = C_INVOICE_5_ID_PARAM_NAME)
	private InvoiceId c_invoice_5_id;

	private final IPaymentBL paymentBL = Services.get(IPaymentBL.class);
	private final IInvoiceDAO invoiceDAO = Services.get(IInvoiceDAO.class);
	private final IAllocationDAO allocationDAO = Services.get(IAllocationDAO.class);

	@Override
	public ProcessPreconditionsResolution checkPreconditionsApplicable(@NonNull final IProcessPreconditionsContext context)
	{
		final IMsgBL iMsgBL = Services.get(IMsgBL.class);

		if (context.isNoSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNoSelection();
		}

		if (!context.isSingleSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		// TODO tbp: reject if the bank statement is not open
		final I_C_BankStatement selectedBankStatement = context.getSelectedModel(I_C_BankStatement.class);
		final DocStatus docStatus = DocStatus.ofCode(selectedBankStatement.getDocStatus());
		if (docStatus.isCompletedOrClosedReversedOrVoided())
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("BankStatement should be open"));
		}

		// there should be a single line selected
		final Set<TableRecordReference> selectedLineReferences = context.getSelectedIncludedRecords();
		if (selectedLineReferences.size() != 1)
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("A single line should be selected."));
		}

		final TableRecordReference reference = selectedLineReferences.iterator().next();
		final I_C_BankStatementLine line = reference.getModel(I_C_BankStatementLine.class);
		if (line == null)
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("A single line should be selected."));
		}

		if (line.getC_BPartner_ID() <= 0)
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("Line should have a Business Partner."));
		}

		return ProcessPreconditionsResolution.accept();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_INVOICE_1_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Invoice.Table_Name)
	public LookupValuesList invoice1LookupProvider()
	{
		return defaultInvoiceLookupProvider();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_INVOICE_2_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Invoice.Table_Name)
	public LookupValuesList invoice2LookupProvider()
	{
		return defaultInvoiceLookupProvider();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_INVOICE_3_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Invoice.Table_Name)
	public LookupValuesList invoice3LookupProvider()
	{
		return defaultInvoiceLookupProvider();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_INVOICE_4_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Invoice.Table_Name)
	public LookupValuesList invoice4LookupProvider()
	{
		return defaultInvoiceLookupProvider();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_INVOICE_5_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Invoice.Table_Name)
	public LookupValuesList invoice5LookupProvider()
	{
		return defaultInvoiceLookupProvider();
	}

	private LookupValuesList defaultInvoiceLookupProvider()
	{
		final I_C_BankStatementLine bankStatementLine = getSelectedBankStatementLine();
		final int bankStatementLineCBPartnerId = bankStatementLine.getC_BPartner_ID();

		final ImmutableSet<InvoiceId> invoiceIds = Services.get(IQueryBL.class).createQueryBuilder(I_C_Invoice.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_C_Invoice.COLUMNNAME_DocStatus, DocStatus.Completed)
				.addEqualsFilter(I_C_Invoice.COLUMNNAME_IsPaid, false)
				.addEqualsFilter(I_C_Invoice.COLUMNNAME_C_BPartner_ID, bankStatementLineCBPartnerId)
				.create()
				.listIds(InvoiceId::ofRepoId);

		final LookupDescriptor invoiceByIdLookupDescriptor = SqlLookupDescriptor.builder()
				.setCtxTableName(I_C_Invoice.Table_Name)
				.setCtxColumnName(I_C_Invoice.COLUMNNAME_C_Invoice_ID)
				.setDisplayType(DisplayType.Search)
				.setWidgetType(DocumentFieldWidgetType.Lookup)
				// TODO tbp: @teo how to .orderby asc???
				.buildForDefaultScope();

		return LookupDataSourceFactory.instance.getLookupDataSource(invoiceByIdLookupDescriptor).findByIds(invoiceIds);
	}

	@Override
	protected String doIt() throws Exception
	{
		just___DO_IT();

		return MSG_OK;
	}

	private void just___DO_IT()
	{
		final I_C_BankStatement bankStatement = getSelectedBankStatement();
		final I_C_BankStatementLine bankStatementLine = getSelectedBankStatementLine();
		final ImmutableList<InvoiceId> invoiceIds = getSelectedInvoices();

		final BankAccountId bankAccountId = BankAccountId.ofRepoId(bankStatement.getC_BP_BankAccount_ID());
		final ImmutableList<PaymentId> paymentIds = retrieveOrCreatePaymentsForInvoices(invoiceIds, bankAccountId, bankStatementLine.getStmtAmt());  // TODO tbp: not sure if stmt amount is the correct one. Help?

		if (paymentIds.size() == 1)
		{
			// Link the single Payment to the BankStatementLine directly
			final I_C_Payment payment = Services.get(IPaymentDAO.class).getById(paymentIds.iterator().next());
			Services.get(IBankStatmentPaymentBL.class).setC_Payment(bankStatementLine, payment);
			InterfaceWrapperHelper.save(bankStatementLine);
			// interceptors will update the bank statement and line
		}
		else
		{
			// Iterate over the Payments and link them to the BankStatementLine via BankStatementLineRef
			bankStatementLine.setIsMultiplePaymentOrInvoice(true);  // TODO tbp: why do we have 2 fields which do pretty much the same thing?
			bankStatementLine.setIsMultiplePayment(true); // TODO tbp: why do we have 2 fields which do pretty much the same thing?
			int lineNumber = 10;
			for (final PaymentId paymentId : paymentIds)
			{
				// Create bank statement line ref
				final I_C_BankStatementLine_Ref lineRef = InterfaceWrapperHelper.newInstance(I_C_BankStatementLine_Ref.class);
				lineRef.setC_BankStatementLine_ID(bankStatementLine.getC_BankStatementLine_ID());
				lineRef.setLine(lineNumber);
				lineNumber += 10;

				final I_C_Payment payment = Services.get(IPaymentDAO.class).getById(paymentId);
				Services.get(IBankStatmentPaymentBL.class).setC_Payment(lineRef, payment);
				InterfaceWrapperHelper.save(lineRef);
				// interceptors will update the bank statement and line
			}
		}
	}

	/**
	 * Iterate over the selected invoices and create/retrieve payments, until the grand total of the invoice or the current line amount is reached
	 */
	private ImmutableList<PaymentId> retrieveOrCreatePaymentsForInvoices(final ImmutableList<InvoiceId> invoiceIds, final BankAccountId bankAccountId, final BigDecimal bankStatementLineAmountLeftForAllocation)
	{
		BigDecimal amountLeftForAllocation = bankStatementLineAmountLeftForAllocation;

		final ImmutableList.Builder<PaymentId> paymentIdsCollector = ImmutableList.builder();
		for (final InvoiceId invoiceId : invoiceIds)
		{
			// TODO tbp: extract this into a method and replace it in 2 places
			if (amountLeftForAllocation.compareTo(BigDecimal.ZERO) <= 0)
			{
				break;
			}
			amountLeftForAllocation = selectOrCreatePaymentsForInvoice(invoiceId, bankAccountId, amountLeftForAllocation, paymentIdsCollector);
		}
		return paymentIdsCollector.build();
	}

	private I_C_BankStatement getSelectedBankStatement()
	{
		final int bankStatementId = getRecord_ID();
		final IBankStatementDAO bankStatementDAO = Services.get(IBankStatementDAO.class);
		return bankStatementDAO.getById(bankStatementId);
	}

	private BigDecimal selectOrCreatePaymentsForInvoice(final InvoiceId invoiceId, final BankAccountId bankAccountId, /*not final*/ BigDecimal amountLeftForAllocation, final ImmutableList.Builder<PaymentId> paymentIDsCollector)
	{
		final I_C_Invoice invoice = invoiceDAO.getByIdInTrx(invoiceId);

		// only allocate payments which summed, are less than the amount left for allocation
		final List<I_C_Payment> cPayments = allocationDAO.retrieveInvoicePayments(invoice);
		for (final I_C_Payment cPayment : cPayments)
		{
			if (amountLeftForAllocation.compareTo(BigDecimal.ZERO) <= 0)
			{
				break;
			}

			final BigDecimal payAmt = cPayment.getPayAmt();
			// if amount left for allocation - pay amount < 0 => skip this payment
			if (amountLeftForAllocation.subtract(payAmt).compareTo(BigDecimal.ZERO) < 0)
			{
				continue;
			}

			amountLeftForAllocation = amountLeftForAllocation.subtract(payAmt);
			paymentIDsCollector.add(PaymentId.ofRepoId(cPayment.getC_Payment_ID()));
		}

		// if the invoice is paid, there's no other payment to create
		if (invoice.isPaid())
		{
			return amountLeftForAllocation;
		}

		final BigDecimal openAmt = invoiceDAO.retrieveOpenAmt(invoiceId).getAsBigDecimal();
		final BigDecimal openAmtPossibleToAllocate = openAmt.min(amountLeftForAllocation); // in an ideal world, openAmt and amountLeftForAllocation are ==

		amountLeftForAllocation = amountLeftForAllocation.subtract(openAmtPossibleToAllocate);
		final I_C_Payment payment = paymentBL.newBuilderOfInvoice(invoice)
				.bpBankAccountId(bankAccountId)
				.payAmt(openAmtPossibleToAllocate)
				// .currencyId() // already set by the builder
				.dateAcct(SystemTime.asLocalDate())  // TODO tbp: what date to set here? something from the BankStatement/line?
				.dateTrx(SystemTime.asLocalDate())   // TODO tbp: what date to set here? something from the BankStatement/line?
				.description("Automatically created from Invoice open amount during BankStatementLine allocation.")
				.tenderType(TenderType.DirectDeposit)
				.createAndProcess();// create and complete the payment.
		paymentIDsCollector.add(PaymentId.ofRepoId(payment.getC_Payment_ID()));

		return amountLeftForAllocation;
	}

	private I_C_BankStatementLine getSelectedBankStatementLine()
	{
		final Integer lineId = getSelectedIncludedRecordIds(I_C_BankStatementLine.class).iterator().next();
		return Services.get(IBankStatementDAO.class).getLineById(lineId);
	}

	private ImmutableList<InvoiceId> getSelectedInvoices()
	{
		final ArrayList<InvoiceId> invoiceIds = new ArrayList<>();
		invoiceIds.add(c_invoice_1_id);
		invoiceIds.add(c_invoice_2_id);
		invoiceIds.add(c_invoice_3_id);
		invoiceIds.add(c_invoice_4_id);
		invoiceIds.add(c_invoice_5_id);
		invoiceIds.removeIf(Objects::isNull);

		return ImmutableList.copyOf(invoiceIds);
	}
}
