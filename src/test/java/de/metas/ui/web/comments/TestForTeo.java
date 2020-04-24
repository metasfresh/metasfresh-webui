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

package de.metas.ui.web.comments;

import de.metas.comments.CommentEntryId;
import de.metas.comments.CommentEntryRepository;
import de.metas.comments.CommentId;
import de.metas.ui.web.comments.json.JSONComment;
import de.metas.ui.web.window.datatypes.json.DateTimeConverters;
import de.metas.user.UserId;
import de.metas.util.time.SystemTime;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.assertj.core.api.Assertions;
import org.compiere.model.I_AD_User;
import org.compiere.model.I_CM_Chat;
import org.compiere.model.I_CM_ChatEntry;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.X_CM_ChatEntry;
import org.compiere.util.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

public class TestForTeo
{

	public static final int AD_USER_ID = 10;
	public static final String THE_USER_NAME = "The User Name";
	private CommentEntryRepository commentEntryRepository;

	private CommentsService commentsService;
	public static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.of(2020, Month.APRIL.getValue(), 23, 1, 1, 1, 0, ZoneId.of("UTC+8"));

	@BeforeEach
	public void init()
	{
		AdempiereTestHelper.get().init();
		SystemTime.setTimeSource(() -> ZONED_DATE_TIME.toInstant().toEpochMilli());

		// all created POs will have this user
		Env.setLoggedUserId(Env.getCtx(), UserId.ofRepoId(AD_USER_ID));

		createDefaultUser();
		commentEntryRepository = new CommentEntryRepository();
		commentsService = new CommentsService(commentEntryRepository);
	}

	@Test
	void zoneIdNotWorking()
	{
		// create test data
		final TableRecordReference tableRecordReference = TableRecordReference.of("DummyTable", 1);
		final CommentId commentId = createChat(tableRecordReference);
		createChatEntry(commentId, "comment1");

		//
		final List<JSONComment> actual = commentsService.getCommentsFor(tableRecordReference);
		System.out.println(actual);

		final String zonedDateTimeString = DateTimeConverters.toJson(ZONED_DATE_TIME, ZoneId.of("UTC+8"));

		final List<JSONComment> expected = Collections.singletonList(
				JSONComment.builder()
						.created(zonedDateTimeString)
						.text("comment1")
						.createdBy(THE_USER_NAME)
						.build()
		);

		Assertions.assertThat(actual.get(0).getCreated()).isEqualTo(expected.get(0).getCreated());
	}

	/**
	 * Not necessary, but helpful to have an actual user name.
	 */
	private void createDefaultUser()
	{
		final I_AD_User user = InterfaceWrapperHelper.newInstance(I_AD_User.class);
		user.setAD_User_ID(AD_USER_ID);
		user.setName(THE_USER_NAME);
		InterfaceWrapperHelper.save(user);
	}

	private CommentId createChat(final TableRecordReference tableRecordReference)
	{
		final I_CM_Chat chat = InterfaceWrapperHelper.newInstance(I_CM_Chat.class);
		chat.setDescription("Table name: " + I_C_BPartner.Table_Name);
		chat.setAD_Table_ID(tableRecordReference.getAD_Table_ID());
		chat.setRecord_ID(tableRecordReference.getRecord_ID());
		InterfaceWrapperHelper.save(chat);
		return CommentId.ofRepoId(chat.getCM_Chat_ID());
	}

	private void createChatEntry(final CommentId commentId, final String characterData)
	{
		final I_CM_ChatEntry chatEntry = InterfaceWrapperHelper.newInstance(I_CM_ChatEntry.class);
		chatEntry.setCM_Chat_ID(commentId.getRepoId());
		chatEntry.setConfidentialType(X_CM_ChatEntry.CONFIDENTIALTYPE_PublicInformation);
		chatEntry.setCharacterData(characterData);
		chatEntry.setChatEntryType(X_CM_ChatEntry.CHATENTRYTYPE_NoteFlat);
		InterfaceWrapperHelper.save(chatEntry);
		CommentEntryId.ofRepoId(chatEntry.getCM_ChatEntry_ID());
	}
}
