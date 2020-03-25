package de.metas.banking.process;

import java.util.Collection;
import java.util.Set;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.SpringContextHolder;
import org.compiere.model.I_C_BankStatement;
import org.compiere.model.I_C_BankStatementLine;

import de.metas.banking.BankStatementId;
import de.metas.banking.BankStatementLineId;
import de.metas.banking.payment.IBankStatementPaymentBL;
import de.metas.banking.service.IBankStatementBL;
import de.metas.banking.service.IBankStatementDAO;
import de.metas.document.engine.DocStatus;
import de.metas.i18n.IMsgBL;
import de.metas.payment.PaymentId;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.ProcessExecutionResult.ViewOpenTarget;
import de.metas.process.ProcessExecutionResult.WebuiViewToOpen;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.ui.web.bankstatement_reconciliation.BankStatementReconciliationView;
import de.metas.ui.web.bankstatement_reconciliation.BankStatementReconciliationViewFactory;
import de.metas.ui.web.bankstatement_reconciliation.BanksStatementReconciliationViewCreateRequest;
import de.metas.ui.web.view.ViewId;
import de.metas.util.Services;
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

abstract class BankStatementBasedProcess extends JavaProcess implements IProcessPrecondition
{
	public static final String BANK_STATEMENT_MUST_BE_COMPLETED_OR_IN_PROGRESS_MSG = "de.metas.banking.process.C_BankStatement_AddBpartnerAndPayment.BankStatement_must_be_Completed_or_In_Progress";
	public static final String A_SINGLE_LINE_SHOULD_BE_SELECTED_MSG = "de.metas.banking.process.C_BankStatement_AddBpartnerAndPayment.A_single_line_should_be_selected";
	public static final String LINE_SHOULD_NOT_HAVE_A_PAYMENT_MSG = "de.metas.banking.process.C_BankStatement_AddBpartnerAndPayment.Line_should_not_have_a_Payment";

	// services
	protected final IMsgBL msgBL = Services.get(IMsgBL.class);
	protected final IBankStatementBL bankStatementBL = Services.get(IBankStatementBL.class);
	protected final IBankStatementDAO bankStatementDAO = Services.get(IBankStatementDAO.class);
	protected final IBankStatementPaymentBL bankStatementPaymentBL = Services.get(IBankStatementPaymentBL.class);
	private final BankStatementReconciliationViewFactory bankStatementReconciliationViewFactory = SpringContextHolder.instance.getBean(BankStatementReconciliationViewFactory.class);

	protected final ProcessPreconditionsResolution checkBankStatementIsDraftOrInProcessOrCompleted(@NonNull final IProcessPreconditionsContext context)
	{
		if (context.isNoSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNoSelection();
		}

		if (!context.isSingleSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		final BankStatementId bankStatementId = BankStatementId.ofRepoId(context.getSingleSelectedRecordId());
		final I_C_BankStatement bankStatement = bankStatementDAO.getById(bankStatementId);
		final DocStatus docStatus = DocStatus.ofCode(bankStatement.getDocStatus());
		if (!docStatus.isDraftedInProgressOrCompleted())
		{
			return ProcessPreconditionsResolution.reject(msgBL.getTranslatableMsgText(BANK_STATEMENT_MUST_BE_COMPLETED_OR_IN_PROGRESS_MSG));
		}

		return ProcessPreconditionsResolution.accept();
	}

	protected final ProcessPreconditionsResolution checkSingleLineSelectedWhichIsNotReconciled(@NonNull final IProcessPreconditionsContext context)
	{
		// there should be a single line selected
		final Set<TableRecordReference> bankStatemementLineRefs = context.getSelectedIncludedRecords();
		if (bankStatemementLineRefs.size() != 1)
		{
			return ProcessPreconditionsResolution.reject(msgBL.getTranslatableMsgText(A_SINGLE_LINE_SHOULD_BE_SELECTED_MSG));
		}

		final TableRecordReference bankStatemementLineRef = bankStatemementLineRefs.iterator().next();
		final BankStatementLineId bankStatementLineId = BankStatementLineId.ofRepoId(bankStatemementLineRef.getRecord_ID());
		final I_C_BankStatementLine line = bankStatementDAO.getLineById(bankStatementLineId);
		if (line.isReconciled())
		{
			return ProcessPreconditionsResolution.reject(msgBL.getTranslatableMsgText(LINE_SHOULD_NOT_HAVE_A_PAYMENT_MSG));
		}

		return ProcessPreconditionsResolution.accept();
	}
	
	protected final I_C_BankStatement getSelectedBankStatement()
	{
		final BankStatementId bankStatementId = BankStatementId.ofRepoId(getRecord_ID());
		return bankStatementDAO.getById(bankStatementId);
	}

	protected final I_C_BankStatementLine getSingleSelectedBankStatementLine()
	{
		final BankStatementLineId lineId = getSingleSelectedBankStatementLineId();
		return bankStatementDAO.getLineById(lineId);
	}

	protected final BankStatementLineId getSingleSelectedBankStatementLineId()
	{
		final Set<Integer> bankStatementLineRepoIds = getSelectedIncludedRecordIds(I_C_BankStatementLine.class);
		if (bankStatementLineRepoIds.isEmpty())
		{
			throw new AdempiereException("@NoSelection@");
		}
		else if (bankStatementLineRepoIds.size() == 1)
		{
			return BankStatementLineId.ofRepoId(bankStatementLineRepoIds.iterator().next());
		}
		else
		{
			throw new AdempiereException("More than one bank statement line selected: " + bankStatementLineRepoIds);
		}
	}

	protected final void openBankStatementReconciliationView(@NonNull final Collection<PaymentId> paymentIds)
	{
		if (paymentIds.isEmpty())
		{
			throw new AdempiereException("@NoPayments@");
		}

		final BankStatementReconciliationView view = bankStatementReconciliationViewFactory.createView(BanksStatementReconciliationViewCreateRequest.builder()
				.bankStatementLineId(getSingleSelectedBankStatementLineId())
				.paymentIds(paymentIds)
				.build());
		final ViewId viewId = view.getViewId();

		getResult().setWebuiViewToOpen(WebuiViewToOpen.builder()
				.viewId(viewId.toJson())
				.target(ViewOpenTarget.ModalOverlay)
				.build());
	}
}
