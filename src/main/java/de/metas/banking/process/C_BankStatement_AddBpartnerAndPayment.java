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

import com.google.common.collect.ImmutableSet;
import de.metas.banking.model.I_C_BankStatement;
import de.metas.banking.model.I_C_BankStatementLine;
import de.metas.banking.service.IBankStatementBL;
import de.metas.banking.service.IBankStatementDAO;
import de.metas.bpartner.BPartnerId;
import de.metas.document.engine.DocStatus;
import de.metas.i18n.IMsgBL;
import de.metas.money.CurrencyId;
import de.metas.money.Money;
import de.metas.payment.PaymentId;
import de.metas.payment.api.IPaymentDAO;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.ui.web.process.descriptor.ProcessParamLookupValuesProvider;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementFieldDescriptor;
import de.metas.ui.web.window.model.lookup.LookupDataSourceFactory;
import de.metas.util.Services;
import lombok.NonNull;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_Payment;

import java.math.BigDecimal;
import java.util.Set;

public class C_BankStatement_AddBpartnerAndPayment extends JavaProcess implements IProcessPrecondition
{
	private static final String C_BPartner_ID_PARAM_NAME = "C_BPartner_ID";
	@Param(parameterName = C_BPartner_ID_PARAM_NAME)
	private BPartnerId bPartnerId;

	private static final String C_Payment_ID_PARAM_NAME = "C_Payment_ID";
	@Param(parameterName = C_Payment_ID_PARAM_NAME)
	private PaymentId paymentId;

	private final IMsgBL iMsgBL = Services.get(IMsgBL.class);
	private final IBankStatementDAO bankStatementDAO = Services.get(IBankStatementDAO.class);

	@Override
	public ProcessPreconditionsResolution checkPreconditionsApplicable(@NonNull final IProcessPreconditionsContext context)
	{
		if (context.isNoSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNoSelection();
		}

		if (!context.isSingleSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		final I_C_BankStatement selectedBankStatement = context.getSelectedModel(I_C_BankStatement.class);
		final DocStatus docStatus = DocStatus.ofCode(selectedBankStatement.getDocStatus());
		if (!docStatus.isCompleted() && !docStatus.isDraftedOrInProgress())
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("BankStatement must be Completed or In Progress"));
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

		if (line.getC_Payment_ID() > 0)
		{
			return ProcessPreconditionsResolution.reject(iMsgBL.getTranslatableMsgText("Line should not have a Payment."));
		}

		return ProcessPreconditionsResolution.accept();
	}

	@ProcessParamLookupValuesProvider(parameterName = C_Payment_ID_PARAM_NAME, numericKey = true, lookupSource = DocumentLayoutElementFieldDescriptor.LookupSource.lookup, lookupTableName = I_C_Payment.Table_Name)
	private LookupValuesList paymentLookupProvider()
	{
		final I_C_BankStatementLine line = getSelectedBankStatementLine();
		final CurrencyId currencyId = CurrencyId.ofRepoId(line.getC_Currency_ID());
		final boolean isReceipt = line.getStmtAmt().signum() >= 0;
		final BigDecimal paymentAmount = isReceipt ? line.getStmtAmt() : line.getStmtAmt().negate();
		final Money money = Money.of(paymentAmount, currencyId);

		final ImmutableSet<PaymentId> paymentIds = Services.get(IPaymentDAO.class).retrieveAllMatchingPayments(isReceipt, bPartnerId, money);

		return LookupDataSourceFactory.instance.searchInTableLookup(I_C_Payment.Table_Name).findByIdsOrdered(paymentIds);
	}

	@Override
	protected String doIt() throws Exception
	{
		doIt(getSelectedBankStatementLine(), paymentId);

		return MSG_OK;
	}

	private void doIt(final I_C_BankStatementLine bankStatementLine, final PaymentId paymentId)
	{
		final IBankStatementBL bankStatementBL = Services.get(IBankStatementBL.class);
		bankStatementBL.setOrCreateAndLinkPaymentToBankStatementLine(bankStatementLine, paymentId);
	}

	private I_C_BankStatementLine getSelectedBankStatementLine()
	{
		final Integer lineId = getSelectedIncludedRecordIds(I_C_BankStatementLine.class).iterator().next();
		return bankStatementDAO.getLineById(lineId);
	}

}
