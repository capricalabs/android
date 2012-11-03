/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.victor.email.MessageListContext;
import com.victor.email.activity.MessagesAdapter;

import java.util.Calendar;

import com.victor.email.R;

public class MessageListItemGroup extends MessageListItem {

	public boolean isGroupItem = true;

	public MessageListItemGroup(Context context) {
        super(context);
        init(context);
        isGroupItem = true;
    }

    public MessageListItemGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
        isGroupItem = true;
    }

    public MessageListItemGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
        isGroupItem = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the checkbox
        if( mMode != MODE_WIDE )
			canvas.drawBitmap(mAdapter.isSelected(this) ? sSelectedIconOn : sSelectedIconOff,
					mCoordinates.checkmarkX, mCoordinates.subjectY, null);
        else
			canvas.drawBitmap(mAdapter.isSelected(this) ? sSelectedIconOn : sSelectedIconOff,
					mCoordinates.checkmarkX, mCoordinates.sendersY, null);

		Paint headingPaint = mRead ? sDefaultPaint : sBoldPaint;
		headingPaint.setColor(getFontColor(mRead ? SENDERS_TEXT_COLOR_READ : SENDERS_TEXT_COLOR_UNREAD));
		headingPaint.setTextSize(mCoordinates.sendersFontSize);
		
		MessagesAdapter.MessagesCursor c = (MessagesAdapter.MessagesCursor)mAdapter.getCursor();
		if( c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_DATE || c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_DATE_DESC ) {
			// Draw the date
			if( mMode != MODE_WIDE )
				canvas.drawText(mFormattedDate, 0, mFormattedDate.length(),
						mCoordinates.sendersX, mCoordinates.subjectY - mCoordinates.sendersAscent, headingPaint);
			else
				canvas.drawText(mFormattedDate, 0, mFormattedDate.length(),
						mCoordinates.sendersX, mCoordinates.sendersY - mCoordinates.sendersAscent, headingPaint);
		} else if( c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SUBJECT || c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SUBJECT_DESC ) {
			// Subject
			if (!TextUtils.isEmpty(mSubject)) {
				if( mMode != MODE_WIDE )
					canvas.drawText(mSubject, 0, mSubject.length(),
						mCoordinates.sendersX, mCoordinates.subjectY - mCoordinates.sendersAscent,
						headingPaint);
				else
					canvas.drawText(mSubject, 0, mSubject.length(),
						mCoordinates.sendersX, mCoordinates.sendersY - mCoordinates.sendersAscent,
						headingPaint);
			}
		} else if( c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SENDERS || c.mListContext.getSortOrder() == MessageListContext.SORT_ORDER_SENDERS_DESC ) {
			// Draw the sender name
			if( mMode != MODE_WIDE )
				canvas.drawText(mFormattedSender, 0, mFormattedSender.length(),
					mCoordinates.sendersX, mCoordinates.subjectY - mCoordinates.sendersAscent,
					headingPaint);
			else
				canvas.drawText(mFormattedSender, 0, mFormattedSender.length(),
					mCoordinates.sendersX, mCoordinates.sendersY - mCoordinates.sendersAscent,
					headingPaint);
		}

		// draw the number of items in this group
		int num = c.numberOfItemsInGroup( (Integer)this.getTag() );
		String formattedNum = "("+String.valueOf( num )+")";
		int dateX = mCoordinates.dateXEnd - (int)headingPaint.measureText(formattedNum, 0, formattedNum.length());
		if( mMode != MODE_WIDE )
			canvas.drawText(formattedNum, 0, formattedNum.length(),
                dateX, mCoordinates.subjectY - mCoordinates.sendersAscent, headingPaint);
		else
			canvas.drawText(formattedNum, 0, formattedNum.length(),
                dateX, mCoordinates.sendersY - mCoordinates.sendersAscent, headingPaint);

        // set special background
        //this.setBackgroundResource( R.drawable.activated_background );
    }

    long mTimeFormatted = 0;
    public void setTimestamp(long timestamp) {
        if (mTimeFormatted != timestamp) {
			Calendar today = Calendar.getInstance();
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis( timestamp );
			int days = today.get( Calendar.YEAR ) * 365 + today.get( Calendar.DAY_OF_YEAR ) - c.get( Calendar.YEAR ) * 365 - c.get( Calendar.DAY_OF_YEAR );
			if( days == 0 )
				mFormattedDate = "Today, "+DateUtils.formatDateTime( mContext, today.getTimeInMillis(), DateUtils.FORMAT_SHOW_DATE );
			else if( days == 1 )
				mFormattedDate = "Yesterday, "+DateUtils.formatDateTime( mContext, today.getTimeInMillis() - DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_DATE );
			else if( days < 7 )
				mFormattedDate = DateUtils.formatDateTime( mContext, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY );
			else if( days < 14 )
				mFormattedDate = "1 week ago";
			else if( days < 28 )
				mFormattedDate = String.valueOf( (int)Math.floor( days / 7 ) )+" weeks ago";
			else if( days < 61 )
				mFormattedDate = "1 month ago";
			else
				mFormattedDate = String.valueOf( (int)Math.floor( days / 30.5 ) )+" months ago";
            mTimeFormatted = timestamp;
        }
    }

    protected void updateBackground() {
        final Drawable newBackground = getContext().getResources().getDrawable( R.drawable.list_pressed_holo );
        if (newBackground != mCurentBackground) {
            // setBackgroundDrawable is a heavy operation.  Only call it when really needed.
            setBackgroundDrawable(newBackground);
            mCurentBackground = newBackground;
        }
    }
}
