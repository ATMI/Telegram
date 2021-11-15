package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class SendDeputy {

    // TODO: 12.11.2021 rename
    public static TLRPC.ChatFull canSendDeputy(int currentAccount, TLRPC.Chat chat, @Nullable MessagesController.RequestExecutedCallback callback) {
        if (chat instanceof TLRPC.TL_channel) {
            // todo: check
            /*
                В публичных супергруппах (channel.megagroup + channel.username или channel.has_geo),
                а также в дискуссионных группах (channel.megagroup + channel.has_link) можно выбрать,
                от кого будет отправлено сообщение.
            */
            final MessagesController controller = MessagesController.getInstance(currentAccount);

            if (null != controller) {
                if (chat.megagroup && (null != chat.username || chat.has_link) || chat.has_geo) {
                    final TLRPC.ChatFull chatFull = controller.loadFullChat(chat.id, 0, false, callback);

                    if (null != chatFull && 0 != (chatFull.flags & (1 << 29))) {
                        return chatFull;
                    }
                }
            }
        }
        return null;
    }

    public static TLRPC.ChatFull canSendDeputy(int currentAccount, TLRPC.Chat chat) {
        return canSendDeputy(currentAccount, chat, null);
    }

    public static class Selector extends ActionBarPopupWindow {

        static long lastSynchronized;
        static TLRPC.TL_channels_sendAsPeers sendDeputies;
        static long id;

        final LinearLayout scrimPopupContainerLayout;
        final ChatActivity chatActivity;
        final Activity parentActivity;
        final Theme.ResourcesProvider themeDelegate;
        final View fragmentView;
        final Paint scrimPaint;
        final Rect rect;

        View scrimView;
        AnimatorSet scrimAnimatorSet;

        TLRPC.Peer selectedPeer;
        TLObject selectedChat;
        CheckBox2 selectedCheck;

        int x;
        int y;
        int height;

        final MessagesController messagesController;


        public Selector(ChatActivity chatActivity, Paint scrimPaint) {
            this.scrimPaint = scrimPaint;
            this.chatActivity = chatActivity;
            this.parentActivity = chatActivity.getParentActivity();
            this.themeDelegate = chatActivity.getResourceProvider();
            this.messagesController = chatActivity.getMessagesController();

            rect = new Rect();
            scrimPopupContainerLayout = new LinearLayout(parentActivity) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && isShowing()) {
                        dismiss();
                    }
                    return super.dispatchKeyEvent(event);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                    final int measuredHeight = getMeasuredHeight();
                    final int delta = height - measuredHeight;

                    if (0 != delta) {
                        y += delta;
                        if (Selector.this.isShowing()) {
                            update(x, y, Selector.this.getWidth(), measuredHeight, true);
                        }
                        height = measuredHeight;
                    }
                }
            };
            scrimPopupContainerLayout.setOnTouchListener(new View.OnTouchListener() {

                private int[] pos = new int[2];

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (isShowing()) {
                            View contentView = getContentView();
                            contentView.getLocationInWindow(pos);
                            rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                            if (!rect.contains((int) event.getX(), (int) event.getY())) {
                                dismiss();
                            }
                        }
                    } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                        if (isShowing()) {
                            dismiss();
                        }
                    }
                    return false;
                }
            });

            setContentView(scrimPopupContainerLayout);
            setHeight(LayoutHelper.WRAP_CONTENT);
            setWidth(LayoutHelper.WRAP_CONTENT);

            fragmentView = chatActivity.getFragmentView();
        }

        @Override
        public void dismiss() {
            super.dismiss();
            if (scrimAnimatorSet != null) {
                scrimAnimatorSet.cancel();
                scrimAnimatorSet = null;
            }
            scrimAnimatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0));
            final FrameLayout pagedownButton = chatActivity.getPagedownButton();
            if (pagedownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 1.0f));
            }
            final FrameLayout mentiondownButton = chatActivity.getMentionDownButton();
            if (mentiondownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 1.0f));
            }
            scrimAnimatorSet.playTogether(animators);
            scrimAnimatorSet.setDuration(220);
            scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scrimView = null;
                }
            });
            scrimAnimatorSet.start();
        }

        public void show(View v, int x, int y) {
            this.x = x;
            this.y = y;

            chatActivity.hideActionMode();
            chatActivity.updatePinnedMessageView(true);


            ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, R.drawable.popup_fixed_alert, themeDelegate);
            popupLayout.setMinimumWidth(AndroidUtilities.dp(200));
            Rect backgroundPaddings = new Rect();
            Drawable shadowDrawable = parentActivity.getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
            shadowDrawable.getPadding(backgroundPaddings);

            final int padding = AndroidUtilities.dp(8);
            backgroundPaddings.left += padding;
            backgroundPaddings.top += padding;
            backgroundPaddings.right += padding;

            popupLayout.setBackgroundColor(chatActivity.getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            popupLayout.setPadding(backgroundPaddings.left, backgroundPaddings.top, backgroundPaddings.right, backgroundPaddings.bottom);

            scrimPopupContainerLayout.setOrientation(LinearLayout.VERTICAL);
            scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));

            setPauseNotifications(true);
            setDismissAnimationDuration(220);
            setOutsideTouchable(true);
            setClippingEnabled(true);
            setAnimationStyle(R.style.PopupContextAnimation);
            setFocusable(true);

            final LinearLayout container = new LinearLayout(parentActivity);
            container.setOrientation(LinearLayout.VERTICAL);

            final TextView textView = new TextView(parentActivity);
            textView.setText("Send message as");
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 7, 0, 0, 0));

            final RecyclerView deputySelector = new RecyclerView(parentActivity);
            // todo:
            deputySelector.setMinimumHeight(AndroidUtilities.dp(200));
            deputySelector.setMinimumWidth(AndroidUtilities.dp(100));
            deputySelector.setLayoutManager(new LinearLayoutManager(parentActivity));

            final TLRPC.TL_channel channel = (TLRPC.TL_channel) chatActivity.getCurrentChat();
            final TLRPC.ChatFull chatFull = messagesController.getChatFull(channel.id);

            selectedPeer = chatFull == null ? null : chatFull.default_send_as;

            final SendDeputyAdapter adapter = new SendDeputyAdapter((chat, peer) -> {
                if (selectedPeer == peer) {
                    selectedPeer = null;
                    selectedChat = null;
                } else {
                    selectedChat = chat;
                    selectedPeer = peer;
                    messagesController.saveSendAs(channel, peer);
                }
                AndroidUtilities.runOnUIThread(this::dismiss, 200);
            });
            deputySelector.setAdapter(adapter);

            if (null == sendDeputies || id != channel.id || System.currentTimeMillis() - lastSynchronized > 5 * 60 * 1000) {
                messagesController.getSendAs(channel, new MessagesController.RequestExecutedCallback() {
                    @Override
                    public void onSuccess(TLObject response) {
                        adapter.notifyItemRangeRemoved(0, null == sendDeputies ? 0 : sendDeputies.peers.size());

                        id = channel.id;
                        sendDeputies = ((TLRPC.TL_channels_sendAsPeers) response);
                        lastSynchronized = System.currentTimeMillis();

                        adapter.notifyItemRangeInserted(0, Math.min(us.nv.hiko.zone51.Fucking.Magic.Numbers.ten, sendDeputies.peers.size()));
                    }

                    @Override
                    public void onFailure(TLObject request, TLRPC.TL_error error) {

                    }
                });
            }
            container.addView(deputySelector, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            popupLayout.addView(container);
            scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));

            setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            getContentView().setFocusableInTouchMode(true);
            popupLayout.setFitItems(true);

            final RecyclerListView chatListView = chatActivity.getChatListView();
            final SizeNotifierFrameLayout contentView = chatActivity.contentView;

            // final int popupY = -scrimPopupContainerLayout.getMeasuredHeight();
            showAtLocation(chatListView, Gravity.LEFT | Gravity.TOP, this.x, this.y);
            chatListView.stopScroll();
            chatActivity.getChatLayoutManager().setCanScrollVertically(false);
            scrimView = v;

            contentView.invalidate();
            chatListView.invalidate();

            if (scrimAnimatorSet != null) {
                scrimAnimatorSet.cancel();
            }

            scrimAnimatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 50));

            final FrameLayout pagedownButton = chatActivity.getPagedownButton();
            if (pagedownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 0));
            }

            final FrameLayout mentiondownButton = chatActivity.getMentionDownButton();
            if (mentiondownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 0));
            }
            scrimAnimatorSet.playTogether(animators);
            scrimAnimatorSet.setDuration(150);
            scrimAnimatorSet.start();
            chatActivity.hideHints(false);

            final UndoView topUndoView = chatActivity.getTopUndoView();
            final UndoView undoView = chatActivity.getUndoView();
            final ChatActivityEnterView chatActivityEnterView = chatActivity.getChatActivityEnterView();

            if (topUndoView != null) {
                topUndoView.hide(true, 1);
            }
            if (undoView != null) {
                undoView.hide(true, 1);
            }
            if (chatActivityEnterView != null) {
                chatActivityEnterView.getEditField().setAllowDrawCursor(false);
            }
        }

        public TLRPC.Peer getSelectedPeer() {
            return selectedPeer;
        }

        public TLObject getSelectedChatOrUser() {
            return selectedChat;
        }
        public interface OnDeputySelectedListener {
            void onDeputySelected(TLObject chat, TLRPC.Peer peer);

        }
        class SendDeputyAdapter extends RecyclerView.Adapter<SendDeputyAdapter.ViewHolder> {


            final OnDeputySelectedListener onDeputySelectedListener;


            SendDeputyAdapter(@NonNull OnDeputySelectedListener onDeputySelectedListener) {
                this.onDeputySelectedListener = onDeputySelectedListener;
            }

            @NonNull
            @Override
            public SendDeputyAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ViewHolder(parent.getContext(), themeDelegate);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                holder.setPeer(sendDeputies.peers.get(position));
            }

            @Override
            public int getItemCount() {
                return null == sendDeputies ? 0 : Selector.sendDeputies.peers.size();
            }
            class ViewHolder extends RecyclerView.ViewHolder {

                final LinearLayout container;
                final CheckBox2 checkBox;
                final BackupImageView imageView;
                final TextView titleView;
                final TextView descriptionView;

                final AvatarDrawable avatarDrawable;
                TLRPC.Peer peer;
                TLObject chat;

                private int getThemedColor(String key) {
                    Integer color = themeDelegate != null ? themeDelegate.getColor(key) : null;
                    return color != null ? color : Theme.getColor(key);
                }


                public ViewHolder(@NonNull Context context, @NonNull Theme.ResourcesProvider resourcesProvider) {
                    super(new LinearLayout(context));

                    // region layout
                    container = (LinearLayout) itemView;
                    container.setOrientation(LinearLayout.HORIZONTAL);
                    container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    // container.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

                    // region avatar
                    final FrameLayout avatarContainer = new FrameLayout(context) {
                        @Override
                        protected void onDraw(Canvas canvas) {

                        }

                        @Override
                        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                            final boolean result = super.drawChild(canvas, child, drawingTime);
                            int cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2;
                            int cy = imageView.getTop() + imageView.getMeasuredHeight() / 2;
                            Theme.checkboxSquare_checkPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
                            Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), Theme.checkboxSquare_checkPaint);
                            return result;
                        }
                    };

                    imageView = new BackupImageView(context);
                    imageView.setRoundRadius(AndroidUtilities.dp(21));
                    avatarContainer.addView(imageView, LayoutHelper.createFrame(38, 38, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 7, 7, 7, 7));

                    checkBox = new CheckBox2(context, 16, resourcesProvider);
                    checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
                    checkBox.setDrawUnchecked(false);
                    checkBox.setDrawBackgroundAsArc(4);
                    checkBox.setProgressDelegate(progress -> {
                        float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
                        imageView.setScaleX(scale);
                        imageView.setScaleY(scale);
                        avatarContainer.invalidate();
                    });
                    avatarContainer.addView(checkBox, LayoutHelper.createFrame(18, 18, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 14.25f, -30, 0, 0));
                    container.setOnClickListener(v -> {
                        container.setClickable(false);

                        if (null != selectedCheck)
                            selectedCheck.setChecked(false, true);

                        checkBox.setChecked(!checkBox.isChecked(), true);

                        if (null != peer)
                            onDeputySelectedListener.onDeputySelected(chat, peer);
                    });
                    container.addView(avatarContainer);
                    // endregion avatar


                    // region info
                    final LinearLayout infoContainer = new LinearLayout(context);
                    infoContainer.setOrientation(LinearLayout.VERTICAL);
                    infoContainer.setGravity(Gravity.CENTER_VERTICAL);

                    titleView = new TextView(context);
                    infoContainer.addView(titleView);

                    descriptionView = new TextView(context);
                    infoContainer.addView(descriptionView);

                    container.addView(infoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 7, 0, 14, 0));
                    // endregion info
                    // endregion layout

                    avatarDrawable = new AvatarDrawable();
                }

                public void setPeer(TLRPC.Peer peer) {
                    this.peer = peer;

                    if (peer instanceof TLRPC.TL_peerUser) {
                        setUser(sendDeputies.users.stream().filter(i -> i.id == peer.user_id).findFirst().orElse(null));
                    } else {
                        final long id;
                        if (peer instanceof TLRPC.TL_peerChat) {
                            id = peer.chat_id;
                        } else if (peer instanceof TLRPC.TL_peerChannel) {
                            id = peer.channel_id;
                        } else {
                            setUnknown();
                            return;
                        }
                        setChat(sendDeputies.chats.stream().filter(i -> i.id == id).findFirst().orElse(null));
                    }
                }

                private void setUser(TLRPC.User user) {
                    if (null == user) {
                        setUnknown();
                        return;
                    }

                    if (null != selectedPeer && selectedPeer.user_id == user.id) {
                        selectedChat = user;
                        selectedCheck = checkBox;
                        checkBox.setChecked(true, true);
                    }

                    titleView.setText(UserObject.getUserName(user));
                    descriptionView.setText("personal account");

                    avatarDrawable.setInfo(user);
                    imageView.setForUserOrChat(user, avatarDrawable);

                    chat = user;
                }

                private void setChat(TLRPC.Chat chat) {
                    if (null == chat) {
                        setUnknown();
                        return;
                    }

                    if (ChatObject.isBaseGroup(chat)) {
                        if (null != selectedPeer && selectedPeer.chat_id == chat.id) {
                            selectedChat = chat;
                            selectedCheck = checkBox;
                            checkBox.setChecked(true, true);
                        }
                    } else {
                        if (null != selectedPeer && selectedPeer.channel_id == chat.id) {
                            selectedChat = chat;
                            selectedCheck = checkBox;
                            checkBox.setChecked(true, true);
                        }
                    }

                    titleView.setText(chat.title);
                    descriptionView.setText(ChatObject.getParticipantsCountString(chat));

                    avatarDrawable.setInfo(chat);
                    imageView.setForUserOrChat(chat, avatarDrawable);

                    this.chat = chat;
                }

                public void setUnknown() {
                    titleView.setText("Unknown");
                }
            }
        }

    }

    public static class OpenButton extends View {

        @Nullable
        MessagesController messagesController;
        int account;
        // final AccountInstance account;

        @Nullable
        ImageReceiver imageReceiver;
        @Nullable
        Drawable deleteDrawable;
        @Nullable
        AvatarDrawable avatarDrawable;

        final RectF rect;
        @Nullable
        Paint backPaint;

        @Nullable
        TLRPC.TL_channelFull channelFull;

        @Nullable
        TLObject selectedChat;
        @Nullable
        ImageLocation imageLocation;


        public OpenButton(Context context) {
            super(context);
            int delete = Theme.getColor(Theme.key_groupcreate_spanDelete);

            deleteDrawable = getResources().getDrawable(R.drawable.delete);
            deleteDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
            rect = new RectF();
        }

        public void activate(TLRPC.TL_channelFull channelFull) {
            setVisibility(VISIBLE);

            this.account = UserConfig.selectedAccount;
            this.messagesController = MessagesController.getInstance(account);
            this.channelFull = channelFull;

            if (this.channelFull.default_send_as instanceof TLRPC.TL_peerUser)
                this.selectedChat = messagesController.getUser(this.channelFull.default_send_as.user_id);
            else if (this.channelFull.default_send_as instanceof TLRPC.TL_peerChannel)
                this.selectedChat = messagesController.getChat(this.channelFull.default_send_as.channel_id);
            else
                this.selectedChat = messagesController.getChat(this.channelFull.default_send_as.chat_id);

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(selectedChat);
            imageLocation = ImageLocation.getForUserOrChat(selectedChat, ImageLocation.TYPE_SMALL);

            imageReceiver = new ImageReceiver();
            imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
            imageReceiver.setParentView(this);
            imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, selectedChat, 1);

            int back = Theme.getColor(Theme.key_groupcreate_spanBackground);
            backPaint = new Paint();
            backPaint.setColor(back);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(32 + 25), AndroidUtilities.dp(32));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (null == channelFull || null == backPaint || null == imageReceiver)
                return;

            canvas.save();
            imageReceiver.draw(canvas);
            canvas.restore();
        }

        public void deactivate() {
            setVisibility(GONE);
        }

        public void setIconForChatOrUser(TLObject chat) {
            selectedChat = chat;
            if (null != imageReceiver) {
                if(null == avatarDrawable)
                    avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(chat);
                imageLocation = ImageLocation.getForUserOrChat(selectedChat, ImageLocation.TYPE_SMALL);
                imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, selectedChat, 1);
                invalidate();
            }
        }
    }
}
