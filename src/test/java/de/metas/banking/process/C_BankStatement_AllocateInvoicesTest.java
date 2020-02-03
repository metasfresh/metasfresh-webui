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
import de.metas.banking.api.BankAccountId;
import de.metas.banking.model.BankStatementId;
import de.metas.banking.model.I_C_BankStatement;
import de.metas.banking.model.I_C_BankStatementLine;
import de.metas.banking.model.I_C_Payment;
import de.metas.banking.model.validator.C_BankStatement;
import de.metas.banking.model.validator.C_BankStatementLine;
import de.metas.banking.model.validator.C_BankStatementLine_Ref;
import de.metas.banking.process.bankstatement_allocateInvoicesProcess.BankStatement_AllocateInvoicesService;
import de.metas.bpartner.BPartnerId;
import de.metas.business.BusinessTestHelper;
import de.metas.document.engine.DocStatus;
import de.metas.money.CurrencyId;
import de.metas.util.Services;
import de.metas.util.time.SystemTime;
import org.adempiere.ad.modelvalidator.IModelInterceptorRegistry;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.test.AdempiereTestHelper;
import org.compiere.model.I_C_BP_BankAccount;
import org.compiere.model.I_C_BPartner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C_BankStatement_AllocateInvoicesTest
{
	private final BankStatement_AllocateInvoicesService bankStatement_allocateInvoicesService = new BankStatement_AllocateInvoicesService();

	@BeforeEach
	void setUp()
	{
		AdempiereTestHelper.get().init();

		final IModelInterceptorRegistry modelInterceptorRegistry = Services.get(IModelInterceptorRegistry.class);
		modelInterceptorRegistry.addModelInterceptor(C_BankStatementLine_Ref.instance);
		modelInterceptorRegistry.addModelInterceptor(C_BankStatement.instance);
		modelInterceptorRegistry.addModelInterceptor(C_BankStatementLine.instance);
	}

	private void paymentChecks(final BigDecimal expectedPayAmt, final int c_payment_id, final boolean expectedIsReceipt, final int c_invoice_id)
	{
		assertNotEquals(0, c_payment_id);
		final I_C_Payment payment = InterfaceWrapperHelper.load(c_payment_id, I_C_Payment.class);
		assertNotNull(payment);
		assertEquals(expectedPayAmt, payment.getPayAmt());
		assertTrue(payment.isReconciled());
		assertEquals(expectedIsReceipt, payment.isReceipt());
		assertEquals(c_invoice_id, payment.getC_Invoice_ID());
		assertEquals(DocStatus.Completed, DocStatus.ofCode(payment.getDocStatus()));

		// can't test `payment.getC_DocType_ID()` as it is set by `PaymentsForInvoicesCreator`, and during test there's no DocTypes
	}

	@Nested
	class PreconditionsTests
	{

		@Test
		void bankStatementMustBeOpen()
		{
			// TODO tbp:
		}

		@Test
		void lineShouldHaveBusinessPartner()
		{
			// TODO tbp:
		}
	}

	@Nested
	class OneBankStatementLine_NoExistingPayment_NoInvoice
	{

		private final Timestamp statementDate = SystemTime.asTimestamp();
		private final String metasfreshIban = "123456";

		private final Timestamp valutaDate = SystemTime.asTimestamp();

		@Test
		void OneInboundBankStatementLine_NoExistingPayment_NoInvoice()
		{
			//
			// create test data
			final BigDecimal lineStmtAmt = BigDecimal.valueOf(123);
			final CurrencyId eurCurrencyId = BusinessTestHelper.getEURCurrencyId();

			final I_C_BPartner metasfreshBPartner = BusinessTestHelper.createBPartner("metasfresh");
			final I_C_BP_BankAccount metasfreshBankAccount = BusinessTestHelper.createBpBankAccount(BPartnerId.ofRepoId(metasfreshBPartner.getC_BPartner_ID()), eurCurrencyId, metasfreshIban);

			final I_C_BankStatement bankStatement = BankStatementTestHelper.createBankStatement(BankAccountId.ofRepoId(metasfreshBankAccount.getC_BP_BankAccount_ID()), "Bank Statement 1", statementDate);

			final I_C_BPartner customerBPartner = BusinessTestHelper.createBPartner("le customer");
			final I_C_BP_BankAccount customerBankAccount = BusinessTestHelper.createBpBankAccount(BPartnerId.ofRepoId(customerBPartner.getC_BPartner_ID()), eurCurrencyId, null);

			final I_C_BankStatementLine bsl = BankStatementTestHelper.createBankStatementLine(
					BankStatementId.ofRepoId(bankStatement.getC_BankStatement_ID()),
					BankAccountId.ofRepoId(customerBankAccount.getC_BP_BankAccount_ID()),
					BPartnerId.ofRepoId(customerBankAccount.getC_BPartner_ID()),
					10,
					statementDate,
					valutaDate,
					lineStmtAmt,
					eurCurrencyId
			);

			//
			// begin testing
			//

			//
			// Call the method
			final C_BankStatement_AllocateInvoices process = new C_BankStatement_AllocateInvoices(bankStatement_allocateInvoicesService);
			process.just___DO_IT(bankStatement, bsl, ImmutableList.of());

			//
			// Checks
			InterfaceWrapperHelper.refresh(bsl);
			final boolean isReceipt = true;
			final int c_invoice_id = 0;
			paymentChecks(lineStmtAmt, bsl.getC_Payment_ID(), isReceipt, c_invoice_id);
			assertFalse(bsl.isMultiplePayment());
			assertFalse(bsl.isMultiplePaymentOrInvoice());
		}

		@Test
		void OneOutboundBankStatementLine_NoExistingPayment_NoInvoice()
		{
			//
			// create test data
			final BigDecimal lineStmtAmt = BigDecimal.valueOf(-123);
			final CurrencyId eurCurrencyId = BusinessTestHelper.getEURCurrencyId();

			final I_C_BPartner metasfreshBPartner = BusinessTestHelper.createBPartner("metasfresh");
			final I_C_BP_BankAccount metasfreshBankAccount = BusinessTestHelper.createBpBankAccount(BPartnerId.ofRepoId(metasfreshBPartner.getC_BPartner_ID()), eurCurrencyId, metasfreshIban);

			final I_C_BankStatement bankStatement = BankStatementTestHelper.createBankStatement(BankAccountId.ofRepoId(metasfreshBankAccount.getC_BP_BankAccount_ID()), "Bank Statement 1", statementDate);

			final I_C_BPartner customerBPartner = BusinessTestHelper.createBPartner("le customer");
			final I_C_BP_BankAccount customerBankAccount = BusinessTestHelper.createBpBankAccount(BPartnerId.ofRepoId(customerBPartner.getC_BPartner_ID()), eurCurrencyId, null);

			final I_C_BankStatementLine bsl = BankStatementTestHelper.createBankStatementLine(
					BankStatementId.ofRepoId(bankStatement.getC_BankStatement_ID()),
					BankAccountId.ofRepoId(customerBankAccount.getC_BP_BankAccount_ID()),
					BPartnerId.ofRepoId(customerBankAccount.getC_BPartner_ID()),
					10,
					statementDate,
					valutaDate,
					lineStmtAmt,
					eurCurrencyId
			);

			//
			// begin testing
			//

			//
			// Call the method
			final C_BankStatement_AllocateInvoices process = new C_BankStatement_AllocateInvoices(bankStatement_allocateInvoicesService);
			process.just___DO_IT(bankStatement, bsl, ImmutableList.of());

			//
			// Checks
			InterfaceWrapperHelper.refresh(bsl);
			final boolean expectedIsReceipt = false;
			final int c_invoice_id = 0;
			final BigDecimal expectedPayAmt = lineStmtAmt.negate();
			paymentChecks(expectedPayAmt, bsl.getC_Payment_ID(), expectedIsReceipt, c_invoice_id);
			assertFalse(bsl.isMultiplePayment());
			assertFalse(bsl.isMultiplePaymentOrInvoice());
		}

	}
}
