package com.fntv.app.ui.home;

import com.fntv.app.api.model.PlayListItem;

import java.util.List;

/**
 * 首页数据单元：大图推荐位 / 行标题 / 横向卡片行（Netflix TV 结构）。
 */
public class HomeItem {

    public static final int TYPE_BILLBOARD = 0;
    public static final int TYPE_ROW_TITLE = 1;
    public static final int TYPE_POSTERS = 2;
    public static final int TYPE_CONTINUE = 3;
    public static final int TYPE_LIVE = 4;
    public static final int TYPE_LOADING = 5;
    public static final int TYPE_TIP = 6;

    public int type;
    public String title;
    public String eyebrow;
    public String action;
    public String guid;
    public String tip;
    public String backdrop;
    public PlayListItem item;
    public List<PlayListItem> items;

    public static HomeItem billboard(PlayListItem item, String eyebrow) {
        HomeItem h = new HomeItem();
        h.type = TYPE_BILLBOARD;
        h.item = item;
        h.eyebrow = eyebrow;
        return h;
    }

    public static HomeItem rowTitle(String title, String action, String guid) {
        HomeItem h = new HomeItem();
        h.type = TYPE_ROW_TITLE;
        h.title = title;
        h.action = action;
        h.guid = guid;
        return h;
    }

    public static HomeItem posters(List<PlayListItem> items) {
        HomeItem h = new HomeItem();
        h.type = TYPE_POSTERS;
        h.items = items;
        return h;
    }

    public static HomeItem continueRow(List<PlayListItem> items) {
        HomeItem h = new HomeItem();
        h.type = TYPE_CONTINUE;
        h.items = items;
        return h;
    }

    public static HomeItem liveRow(List<PlayListItem> items) {
        HomeItem h = new HomeItem();
        h.type = TYPE_LIVE;
        h.items = items;
        return h;
    }

    public static HomeItem loading() {
        HomeItem h = new HomeItem();
        h.type = TYPE_LOADING;
        return h;
    }

    public static HomeItem tip(String text) {
        HomeItem h = new HomeItem();
        h.type = TYPE_TIP;
        h.tip = text;
        return h;
    }
}
