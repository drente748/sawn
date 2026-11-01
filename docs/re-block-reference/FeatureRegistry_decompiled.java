package defpackage;

import com.google.gson.a;
import j$.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class mp {
    public static final mp a;
    public static final /* synthetic */ r33[] b;
    public static final a c;
    public static final Object d;
    public static final qi0 e;

    static {
        ho4 ho4Var = new ho4(mp.class, "AllSupportedFeatures", "getAllSupportedFeatures()Ljava/util/Map;", 0);
        hy4.a.getClass();
        b = new r33[]{ho4Var};
        a = new mp();
        c = new a();
        d = zk3.s(new db4("com.google.android.youtube", vs0.o(new f42("youtube_shorts", "com.google.android.youtube", "Shorts", new bm1(vs0.p("reel_watch_fragment_root", "reel_recycler")), vs0.o("global_action:navigate_back")))), new db4("app.revanced.android.youtube", vs0.o(new f42("revanced_youtube_shorts", "app.revanced.android.youtube", "Shorts", new bm1(vs0.p("reel_watch_fragment_root", "reel_recycler")), vs0.o("global_action:navigate_back")))), new db4("com.instagram.android", vs0.o(new f42("instagram_stories", "com.instagram.android", "Reels", new bm1(vs0.o("clips_viewer_view_pager")), vs0.o("global_action:navigate_back")))), new db4("com.whatsapp", vs0.o(new f42("whatsapp_updates", "com.whatsapp", "Updates", new bm1(vs0.o("updates_list")), vs0.o("global_action:navigate_back")))), new db4("com.snapchat.android", vs0.o(new f42("snapchat_spotlight", "com.snapchat.android", "Spotlight", new bm1(vs0.o("spotlight_container")), vs0.o("global_action:navigate_back")))));
        Duration durationOfSeconds = Duration.ofSeconds(600L);
        yz2.i(durationOfSeconds, "ofSeconds(...)");
        e = new qi0(durationOfSeconds, new i3(5));
    }

    public static f42 a(String str) {
        Object obj;
        yz2.j(str, "featureId");
        int i = 0;
        ArrayList arrayListJ = vs0.j(((Map) e.b(b[0])).values());
        int size = arrayListJ.size();
        while (i < size) {
            obj = arrayListJ.get(i);
            i++;
            if (yz2.e(((f42) obj).c(), str)) {
                return (f42) obj;
            }
        }
        obj = null;
        return (f42) obj;
    }

    public static List b(String str) {
        yz2.j(str, "packageName");
        List list = (List) ((Map) e.b(b[0])).get(str);
        return list == null ? py1.x : list;
    }
}
