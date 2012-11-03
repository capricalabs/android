/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.victor.email.activity;

import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.victor.email.activity.MessageListFragment;
import com.victor.email.Controller;
import com.victor.email.Email;
import com.victor.email.MessageListContext;
import com.victor.email.ResourceHelper;
import com.victor.email.data.ThrottlingCursorLoader;
import com.victor.emailcommon.Logging;
import com.victor.emailcommon.mail.MessagingException;
import com.victor.emailcommon.provider.Account;
import com.victor.emailcommon.provider.EmailContent;
import com.victor.emailcommon.provider.EmailContent.Message;
import com.victor.emailcommon.provider.EmailContent.MessageColumns;
import com.victor.emailcommon.provider.Mailbox;
import com.victor.emailcommon.utility.TextUtilities;
import com.victor.emailcommon.utility.Utility;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;


/**
 * This class implements the adapter for displaying messages based on cursors.
 */
/* package */ class MessagesAdapter extends CursorAdapter {
    private static final String STATE_CHECKED_ITEMS =
            "com.victor.email.activity.MessagesAdapter.checkedItems";

    /* package */ static final String[] MESSAGE_PROJECTION = new String[] {
        EmailContent.RECORD_ID, MessageColumns.MAILBOX_KEY, MessageColumns.ACCOUNT_KEY,
        MessageColumns.DISPLAY_NAME, MessageColumns.SUBJECT, MessageColumns.TIMESTAMP,
        MessageColumns.FLAG_READ, MessageColumns.FLAG_FAVORITE, MessageColumns.FLAG_ATTACHMENT,
        MessageColumns.FLAGS, MessageColumns.SNIPPET
    };

    public static final int COLUMN_ID = 0;
    public static final int COLUMN_MAILBOX_KEY = 1;
    public static final int COLUMN_ACCOUNT_KEY = 2;
    public static final int COLUMN_DISPLAY_NAME = 3;
    public static final int COLUMN_SUBJECT = 4;
    public static final int COLUMN_DATE = 5;
    public static final int COLUMN_READ = 6;
    public static final int COLUMN_FAVORITE = 7;
    public static final int COLUMN_ATTACHMENTS = 8;
    public static final int COLUMN_FLAGS = 9;
    public static final int COLUMN_SNIPPET = 10;

    private final ResourceHelper mResourceHelper;

    /** If true, show color chips. */
    private boolean mShowColorChips;

    /** If not null, the query represented by this group of messages */
    private String mQuery;

    /**
     * Set of seleced message IDs.
     */
    private final HashSet<Long> mSelectedSet = new HashSet<Long>();
    private final HashSet<Long> mSelectedGroupSet = new HashSet<Long>();

    /**
     * Callback from MessageListAdapter.  All methods are called on the UI thread.
     */
    public interface Callback {
        /** Called when the use starts/unstars a message */
        void onAdapterFavoriteChanged(MessageListItem itemView, boolean newFavorite);
        /** Called when the user selects/unselects a message */
        void onAdapterSelectedChanged(MessageListItem itemView, boolean newSelected,
                int mSelectedCount);
        boolean isThreadViewAllowed();
    }

    private final Callback mCallback;

    private ThreePaneLayout mLayout;

    /**
     * The actual return type from the loader.
     */
    public static class MessagesCursor extends CursorWrapper {
        /**  Whether the mailbox is found. */
        public final boolean mIsFound;
        /** {@link Account} that owns the mailbox.  Null for combined mailboxes. */
        public final Account mAccount;
        /** {@link Mailbox} for the loaded mailbox. Null for combined mailboxes. */
        public final Mailbox mMailbox;
        /** {@code true} if the account is an EAS account */
        public final boolean mIsEasAccount;
        /** {@code true} if the loaded mailbox can be refreshed. */
        public final boolean mIsRefreshable;
        /** the number of accounts currently configured. */
        public final int mCountTotalAccounts;

        public final MessageListContext mListContext;
        public MessageListFragment mListFragment;

        private ArrayList<MessageGroup> mGroups;
        private int mLastPosition = -1;

        private class MessageGroup {
			public boolean mIsGroupItem;
			public boolean mIsGroupShown;
			public int mNewPosition;
			public int mNumberOfItems;
			
			public MessageGroup( boolean isGroupItem, boolean isGroupShown, int newPosition ) {
				mIsGroupItem = isGroupItem;
				mIsGroupShown = isGroupShown;
				mNewPosition = newPosition;
				mNumberOfItems = 0;
			}

			public String toString() {
				return String.valueOf( mIsGroupItem )+" "+String.valueOf( mIsGroupShown )+" "+String.valueOf( mNewPosition );
			}
		}

        private MessagesCursor(Cursor cursor,
                boolean found, Account account, Mailbox mailbox, boolean isEasAccount,
                boolean isRefreshable, int countTotalAccounts, MessageListContext listContext, MessageListFragment listFragment) {
            super(cursor);
            mIsFound = found;
            mAccount = account;
            mMailbox = mailbox;
            mIsEasAccount = isEasAccount;
            mIsRefreshable = isRefreshable;
            mCountTotalAccounts = countTotalAccounts;
            mListContext = listContext;
            mListFragment = listFragment;
            groupItems();
        }

		/**
		 * Go through the cursor and insert grouping items at the proper positions.
		 * Grouping items will show as headers in the view and allow accordion-like
		 * operation. Since we are not allowed to insert into a cursor, we will handle
		 * this with 2 array lists and special positions (overriding the position methods)
		 */
        private void groupItems() {
			Calendar last_date = Calendar.getInstance();
			Calendar cur_date = Calendar.getInstance();
			String last_subject = null;
			String last_sender = null;
			int numGroupItems = 0;
			mGroups = new ArrayList<MessageGroup>();
			MessageGroup lastGroup = null;
			super.moveToPosition(-1);
			while( super.moveToNext() ) {
				cur_date.setTimeInMillis( getLong( COLUMN_DATE ) );
				if( mListContext.getSortOrder() == MessageListContext.SORT_ORDER_DATE || mListContext.getSortOrder() == MessageListContext.SORT_ORDER_DATE_DESC ) {
					if( isFirst() || !sameDay( last_date, cur_date ) || !sameWeek( last_date, cur_date ) || !sameMonth( last_date, cur_date ) ) {
						numGroupItems++;
						lastGroup = new MessageGroup( true, true, super.getPosition() );
						mGroups.add( lastGroup );
					}
				} else if( mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SUBJECT || mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SUBJECT_DESC ) {
					if( isFirst() || ( getString( COLUMN_SUBJECT ) == null && last_subject != null ) || !getString( COLUMN_SUBJECT ).equals( last_subject ) ) {
						numGroupItems++;
						lastGroup = new MessageGroup( true, true, super.getPosition() );
						mGroups.add( lastGroup );
					}
				} else if( mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SENDERS || mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SENDERS_DESC ) {
					if( isFirst() || ( getString( COLUMN_DISPLAY_NAME ) == null && last_sender != null ) || !getString( COLUMN_DISPLAY_NAME ).equals( last_sender ) ) {
						numGroupItems++;
						lastGroup = new MessageGroup( true, true, super.getPosition() );
						mGroups.add( lastGroup );
					}
				}
				MessageGroup mg = new MessageGroup( false, true, super.getPosition() );
				mGroups.add( mg );
				if( lastGroup != null )
					lastGroup.mNumberOfItems++;
				last_date.setTimeInMillis( cur_date.getTimeInMillis() );
				last_subject = getString( COLUMN_SUBJECT );
				last_sender = getString( COLUMN_DISPLAY_NAME );
			}
			moveToPosition(-1);
			//Log.i( "k9Victor", mGroupItems.toString() );
			//Log.i( "k9Victor", mNewPositions.toString() );
		}

		private boolean sameDay( Calendar d1, Calendar d2 ) {
			Calendar today = Calendar.getInstance();
			// check if in same week
			if( today.getTimeInMillis() - d1.getTimeInMillis() > DateUtils.WEEK_IN_MILLIS )
				return true;
			return d1.get( Calendar.YEAR ) == d2.get( Calendar.YEAR ) && d1.get( Calendar.DAY_OF_YEAR ) == d2.get( Calendar.DAY_OF_YEAR );
		}

		private boolean sameWeek( Calendar d1, Calendar d2 ) {
			Calendar today = Calendar.getInstance();
			// check if in same 4-weeks period
			if( today.getTimeInMillis() - d1.getTimeInMillis() > 4 * DateUtils.WEEK_IN_MILLIS )
				return true;
			if( today.getTimeInMillis() - d1.getTimeInMillis() <= DateUtils.WEEK_IN_MILLIS )
				return true;
			int d1diff = (int)Math.floor( ( today.getTimeInMillis() - d1.getTimeInMillis() ) / DateUtils.WEEK_IN_MILLIS );
			int d2diff = (int)Math.floor( ( today.getTimeInMillis() - d2.getTimeInMillis() ) / DateUtils.WEEK_IN_MILLIS );
			return d1diff == d2diff;
		}

		private boolean sameMonth( Calendar d1, Calendar d2 ) {
			Calendar today = Calendar.getInstance();
			// check if in same month
			if( today.getTimeInMillis() - d1.getTimeInMillis() <= 4 * DateUtils.WEEK_IN_MILLIS )
				return true;
			// special case of 28+ days until 61
			if( today.getTimeInMillis() - d1.getTimeInMillis() < 61 * DateUtils.DAY_IN_MILLIS && today.getTimeInMillis() - d2.getTimeInMillis() < 61 * DateUtils.DAY_IN_MILLIS )
				return true;
			int d1diff = (int)Math.floor( ( today.getTimeInMillis() - d1.getTimeInMillis() ) / ( 30.5 * DateUtils.DAY_IN_MILLIS ) );
			int d2diff = (int)Math.floor( ( today.getTimeInMillis() - d2.getTimeInMillis() ) / ( 30.5 * DateUtils.DAY_IN_MILLIS ) );
			return d1diff == d2diff;
		}

		public int getCount() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.getCount();
			return getCountVisible();
		}

		public int getCountVisible() {
			int count = 0;
			for( int i = 0; i < mGroups.size(); i++ )
				if( mGroups.get( i ).mIsGroupShown || mGroups.get( i ).mIsGroupItem )
					count++;
			return count;
		}

		public int getPosition() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.getPosition();
			return mLastPosition;
		}

		public int getVisiblePosition( int position ) {
			if( position < 0 )
				return position;
			int vCount = 0, i = 0;
			for( i = 0; i < mGroups.size(); i++ ) {
				if( mGroups.get( i ).mIsGroupShown || mGroups.get( i ).mIsGroupItem ) {
					if( vCount == position )
						break;
					vCount++;
				}
			}
			//Log.i( "K9Victor", "getVisiblePosition: "+String.valueOf( position )+" -> "+String.valueOf( i ) );
			return i;
		}

		public boolean moveToPosition( int position ) {
			if( !mListFragment.isThreadViewAllowed() )
				return super.moveToPosition( position );
			int newPosition = position;
			int vPosition = getVisiblePosition( position );
			if( vPosition >= 0 && mGroups.size() > 0 ) {
				if( vPosition >= mGroups.size() )
					newPosition = mGroups.get( mGroups.size() - 1 ).mNewPosition + 1;
				else
					newPosition = mGroups.get( vPosition ).mNewPosition;
			}
			if( super.moveToPosition( newPosition ) || newPosition == -1 ) {
				mLastPosition = position;
				//Log.i( "K9Victor", "moveToPosition - position: "+String.valueOf( position )+", newPosition: "+String.valueOf( newPosition )+" true" );
				return true;
			} else {
				//Log.i( "K9Victor", "moveToPosition - position: "+String.valueOf( position )+", newPosition: "+String.valueOf( newPosition )+" false" );
				return false;
			}
		}

		public boolean moveToNext() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.moveToNext();
			return moveToPosition( mLastPosition+1 );
		}

		public boolean moveToPrevious() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.moveToPrevious();
			return moveToPosition( mLastPosition-1 );
		}

		public boolean move( int offset ) {
			if( !mListFragment.isThreadViewAllowed() )
				return super.move( offset );
			return moveToPosition( mLastPosition + offset );
		}

		public boolean moveToFirst() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.moveToFirst();
			return moveToPosition( 0 );
		}

		public boolean moveToLast() {
			if( !mListFragment.isThreadViewAllowed() )
				return super.moveToLast();
			return moveToPosition( getCount() - 1 );
		}

		public boolean isGroupItem() {
			if( !mListFragment.isThreadViewAllowed() )
				return false;
			//Log.i( "K9Victor", "isGroupItem - mLastPosition: "+String.valueOf( mLastPosition )+", super.getPosition():"+String.valueOf( super.getPosition() )+" "+String.valueOf( mGroupItems.get( mLastPosition ).booleanValue() ) );
			return mGroups.get( getVisiblePosition( getPosition() ) ).mIsGroupItem;
		}

		public void toggleGroup( int position ) {
			//Log.i( "K9Victor", "toggleGroup: "+String.valueOf( position ) );
			moveToPosition( position );
			if( !isGroupItem() )
				return;
			int vPosition = getVisiblePosition( getPosition() );
			boolean show = !mGroups.get( vPosition ).mIsGroupShown;
			mGroups.get( vPosition ).mIsGroupShown = show;
			while( ++vPosition < mGroups.size() && !mGroups.get( vPosition ).mIsGroupItem ) {
				mGroups.get( vPosition ).mIsGroupShown = show;
			}
			//Log.i( "K9Victor", mGroups.toString() );
		}

		public boolean showGroup( int position ) {
			//Log.i( "K9Victor", "showGroup: "+String.valueOf( position ) );
			if( !mGroups.get( getVisiblePosition( position ) ).mIsGroupShown ) {
				toggleGroup( position );
				return true;
			}
			return false;
		}

		public int numberOfItemsInGroup( int position ) {
			moveToPosition( position );
			if( !isGroupItem() )
				return 0;
			return mGroups.get( getVisiblePosition( getPosition() ) ).mNumberOfItems;
		}
	}

    public MessagesAdapter(Context context, Callback callback) {
        super(context.getApplicationContext(), null, 0 /* no auto requery */);
        mResourceHelper = ResourceHelper.getInstance(context);
        mCallback = callback;
    }

    public void setLayout(ThreePaneLayout layout) {
        mLayout = layout;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putLongArray(STATE_CHECKED_ITEMS, Utility.toPrimitiveLongArray(getSelectedSet()));
    }

    public void loadState(Bundle savedInstanceState) {
        Set<Long> checkedset = getSelectedSet();
        checkedset.clear();
        for (long l: savedInstanceState.getLongArray(STATE_CHECKED_ITEMS)) {
            checkedset.add(l);
        }
        notifyDataSetChanged();
    }

    /**
     * Set true for combined mailboxes.
     */
    public void setShowColorChips(boolean show) {
        mShowColorChips = show;
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    public Set<Long> getSelectedSet() {
        return mSelectedSet;
    }

    public Set<Long> getSelectedGroupSet() {
        return mSelectedGroupSet;
    }

    /**
     * Clear the selection.  It's preferable to calling {@link Set#clear()} on
     * {@link #getSelectedSet()}, because it also notifies observers.
     */
    public void clearSelection() {
        Set<Long> checkedset = getSelectedSet();
        if (checkedset.size() > 0) {
            checkedset.clear();
            checkedset = getSelectedGroupSet();
            checkedset.clear();
            notifyDataSetChanged();
        }
    }

    public boolean isSelected(MessageListItem itemView) {
		//Log.i( "K9Victor", "isSelected: "+String.valueOf( itemView.mMessageId ) );
		if( itemView instanceof MessageListItemGroup )
			return getSelectedGroupSet().contains(itemView.mMessageId);
		else
			return getSelectedSet().contains(itemView.mMessageId);
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
		// we should call parent without convertView to make sure new Views are always created
		// so that system does not reuse ItemGroup view where Item view must be used and vice versa
		View v = super.getView( position, null, parent );
		v.setTag( position );
		return v;
	}

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Reset the view (in case it was recycled) and prepare for binding
        MessageListItem itemView = (MessageListItem) view;
        itemView.bindViewInit(this, mLayout);

        // TODO: just move thise all to a MessageListItem.bindTo(cursor) so that the fields can
        // be private, and their inter-dependence when they change can be abstracted away.

        // Load the public fields in the view (for later use)
        itemView.mMessageId = cursor.getLong(COLUMN_ID);
        if( itemView instanceof MessageListItemGroup )
			itemView.mMessageId += 1000000; // make this large enough, so it is different than the first normal message row in the group
        itemView.mMailboxId = cursor.getLong(COLUMN_MAILBOX_KEY);
        final long accountId = cursor.getLong(COLUMN_ACCOUNT_KEY);
        itemView.mAccountId = accountId;

        boolean isRead = cursor.getInt(COLUMN_READ) != 0;
        boolean readChanged = isRead != itemView.mRead;
        itemView.mRead = isRead;
        itemView.mIsFavorite = cursor.getInt(COLUMN_FAVORITE) != 0;
        final int flags = cursor.getInt(COLUMN_FLAGS);
        itemView.mHasInvite = (flags & Message.FLAG_INCOMING_MEETING_INVITE) != 0;
        itemView.mHasBeenRepliedTo = (flags & Message.FLAG_REPLIED_TO) != 0;
        itemView.mHasBeenForwarded = (flags & Message.FLAG_FORWARDED) != 0;
        itemView.mHasAttachment = cursor.getInt(COLUMN_ATTACHMENTS) != 0;
        itemView.setTimestamp(cursor.getLong(COLUMN_DATE));
        itemView.mSender = cursor.getString(COLUMN_DISPLAY_NAME);
        itemView.setText(
                cursor.getString(COLUMN_SUBJECT), cursor.getString(COLUMN_SNIPPET), readChanged);
        itemView.mColorChipPaint =
            mShowColorChips ? mResourceHelper.getAccountColorPaint(accountId) : null;

        if (mQuery != null && itemView.mSnippet != null) {
            itemView.mSnippet =
                TextUtilities.highlightTermsInText(cursor.getString(COLUMN_SNIPPET), mQuery);
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
		if( ((MessagesCursor)cursor).isGroupItem() ) {
			//Log.i( "K9Victor", "newView: group item" );
			MessageListItemGroup item = new MessageListItemGroup(context);
			item.setVisibility(View.VISIBLE);
			return item;
		}
		//Log.i( "K9Victor", "newView: normal item" );
        MessageListItem item = new MessageListItem(context);
        item.setVisibility(View.VISIBLE);
        return item;
    }

    public boolean isThreadViewAllowed() {
		return mCallback.isThreadViewAllowed();
	}

    public void toggleSelected(MessageListItem itemView) {
        updateSelected(itemView, !isSelected(itemView));
    }
    

    /**
     * This is used as a callback from the list items, to set the selected state
     *
     * <p>Must be called on the UI thread.
     *
     * @param itemView the item being changed
     * @param newSelected the new value of the selected flag (checkbox state)
     */
    private void updateSelected(MessageListItem itemView, boolean newSelected) {
		if( itemView instanceof MessageListItemGroup ) {
			// group add/remove items underneath
			if (newSelected) {
				mSelectedGroupSet.add( itemView.mMessageId );
			} else {
				mSelectedGroupSet.remove( itemView.mMessageId );
			}
			int position = (Integer)itemView.getTag();
			MessagesCursor c = (MessagesCursor)getCursor();
			if( c.showGroup( position ) )
				itemView.getParent().getParent().requestLayout();
			c.moveToPosition( position );
			while( c.moveToNext() && !c.isGroupItem() ) {
				if (newSelected) {
					mSelectedSet.add( c.getLong(COLUMN_ID) );
				} else {
					mSelectedSet.remove( c.getLong(COLUMN_ID) );
				}
			}
			notifyDataSetChanged();
		} else {
			if (newSelected) {
				mSelectedSet.add(itemView.mMessageId);
			} else {
				mSelectedSet.remove(itemView.mMessageId);
			}
		}
        if (mCallback != null) {
            mCallback.onAdapterSelectedChanged(itemView, newSelected, mSelectedSet.size());
        }
    }

    /**
     * This is used as a callback from the list items, to set the favorite state
     *
     * <p>Must be called on the UI thread.
     *
     * @param itemView the item being changed
     * @param newFavorite the new value of the favorite flag (star state)
     */
    public void updateFavorite(MessageListItem itemView, boolean newFavorite) {
        changeFavoriteIcon(itemView, newFavorite);
        if (mCallback != null) {
            mCallback.onAdapterFavoriteChanged(itemView, newFavorite);
        }
    }

    private void changeFavoriteIcon(MessageListItem view, boolean isFavorite) {
        view.invalidate();
    }

    /**
     * Creates the loader for {@link MessageListFragment}.
     *
     * @return always of {@link MessagesCursor}.
     */
    public static Loader<Cursor> createLoader(Context context, MessageListContext listContext, MessageListFragment listFragment ) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, "MessagesAdapter createLoader listContext=" + listContext);
        }
        return listContext.isSearch()
                ? new SearchCursorLoader(context, listContext)
                : new MessagesCursorLoader(context, listContext, listFragment );
    }

    private static class MessagesCursorLoader extends ThrottlingCursorLoader {
        protected final Context mContext;
        private final long mAccountId;
        private final long mMailboxId;
        private final MessageListContext mListContext;
        private MessageListFragment mListFragment;

        public MessagesCursorLoader(Context context, MessageListContext listContext, MessageListFragment listFragment) {
            // Initialize with no where clause.  We'll set it later.
            super(context, EmailContent.Message.CONTENT_URI,
                    MESSAGE_PROJECTION, null, null,
                    listContext.getMessageSortOrder());
            mContext = context;
            mAccountId = listContext.mAccountId;
            mMailboxId = listContext.getMailboxId();
            mListContext = listContext;
            mListFragment = listFragment;
        }

        @Override
        public Cursor loadInBackground() {
            // Build the where cause (which can't be done on the UI thread.)
            setSelection(Message.buildMessageListSelection(mContext, mAccountId, mMailboxId));
            // Then do a query to get the cursor
            return loadExtras(super.loadInBackground());
        }

        private Cursor loadExtras(Cursor baseCursor) {
            boolean found = false;
            Account account = null;
            Mailbox mailbox = null;
            boolean isEasAccount = false;
            boolean isRefreshable = false;

            if (mMailboxId < 0) {
                // Magic mailbox.
                found = true;
            } else {
                mailbox = Mailbox.restoreMailboxWithId(mContext, mMailboxId);
                if (mailbox != null) {
                    account = Account.restoreAccountWithId(mContext, mailbox.mAccountKey);
                    if (account != null) {
                        found = true;
                        isEasAccount = account.isEasAccount(mContext) ;
                        isRefreshable = Mailbox.isRefreshable(mContext, mMailboxId);
                    } else { // Account removed?
                        mailbox = null;
                    }
                }
            }
            final int countAccounts = EmailContent.count(mContext, Account.CONTENT_URI);
            return wrapCursor(baseCursor, found, account, mailbox, isEasAccount,
                    isRefreshable, countAccounts);
        }

        /**
         * Wraps a basic cursor containing raw messages with information about the context of
         * the list that's being loaded, such as the account and the mailbox the messages
         * are for.
         * Subclasses may extend this to wrap with additional data.
         */
        protected Cursor wrapCursor(Cursor cursor,
                boolean found, Account account, Mailbox mailbox, boolean isEasAccount,
                boolean isRefreshable, int countTotalAccounts) {
            return new MessagesCursor(cursor, found, account, mailbox, isEasAccount,
                    isRefreshable, countTotalAccounts, mListContext, mListFragment);
        }
    }

    public static class SearchResultsCursor extends MessagesCursor {
        private final Mailbox mSearchedMailbox;
        private final int mResultsCount;
        private SearchResultsCursor(Cursor cursor,
                boolean found, Account account, Mailbox mailbox, boolean isEasAccount,
                boolean isRefreshable, int countTotalAccounts,
                Mailbox searchedMailbox, int resultsCount) {
            super(cursor, found, account, mailbox, isEasAccount,
                    isRefreshable, countTotalAccounts, null, null);
            mSearchedMailbox = searchedMailbox;
            mResultsCount = resultsCount;
        }

        /**
         * @return the total number of results that match the given search query. Note that
         *     there may not be that many items loaded in the cursor yet.
         */
        public int getResultsCount() {
            return mResultsCount;
        }

        public Mailbox getSearchedMailbox() {
            return mSearchedMailbox;
        }
    }

    /**
     * A special loader used to perform a search.
     */
    private static class SearchCursorLoader extends MessagesCursorLoader {
        private final MessageListContext mListContext;
        private int mResultsCount = -1;
        private Mailbox mSearchedMailbox = null;

        public SearchCursorLoader(Context context, MessageListContext listContext) {
            super(context, listContext, null);
            Preconditions.checkArgument(listContext.isSearch());
            mListContext = listContext;
        }

        @Override
        public Cursor loadInBackground() {
            if (mResultsCount >= 0) {
                // Result count known - the initial search meta data must have completed.
                return super.loadInBackground();
            }

            if (mSearchedMailbox == null) {
                mSearchedMailbox = Mailbox.restoreMailboxWithId(
                        mContext, mListContext.getSearchedMailbox());
            }

            // The search results info hasn't even been loaded yet, so the Controller has not yet
            // initialized the search mailbox properly. Kick off the search first.
            Controller controller = Controller.getInstance(mContext);
            try {
                mResultsCount = controller.searchMessages(
                        mListContext.mAccountId, mListContext.getSearchParams());
            } catch (MessagingException e) {
            }

            // Return whatever the super would do, now that we know the results are ready.
            // After this point, it should behave as a normal mailbox load for messages.
            return super.loadInBackground();
        }

        @Override
        protected Cursor wrapCursor(Cursor cursor,
                boolean found, Account account, Mailbox mailbox, boolean isEasAccount,
                boolean isRefreshable, int countTotalAccounts) {
            return new SearchResultsCursor(cursor, found, account, mailbox, isEasAccount,
                    isRefreshable, countTotalAccounts, mSearchedMailbox, mResultsCount);
        }
    }
}
