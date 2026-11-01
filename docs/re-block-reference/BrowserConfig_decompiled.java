package defpackage;

import com.github.mikephil.charting.BuildConfig;
import com.google.gson.a;
import com.google.gson.b;
import defpackage.a33;
import defpackage.ag0;
import defpackage.cg0;
import defpackage.v23;
import defpackage.x23;
import defpackage.yz2;
import j$.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public final class cg0 {
    public static final bg0 g = new bg0();
    public static final a h;
    public static final qi0 i;
    public static final LinkedHashMap j;
    public final String a;
    public final String b;
    public final boolean c;
    public final Set d;
    public final ArrayList e;
    public ah2 f;

    static {
        b bVar = new b() { // from class: block.libraries.usage.web.BrowserConfig$Companion$gsonAdapter$1
            @Override // com.google.gson.b
            public final Object b(v23 v23Var) throws IOException {
                yz2.j(v23Var, "reader");
                v23Var.e();
                String strW = BuildConfig.FLAVOR;
                boolean zF = false;
                String strW2 = BuildConfig.FLAVOR;
                while (true) {
                    x23 x23VarZ = v23Var.Z();
                    int i2 = x23VarZ == null ? -1 : ag0.a[x23VarZ.ordinal()];
                    if (i2 == 1) {
                        v23Var.o();
                        return new cg0(strW, strW2, zF);
                    }
                    if (i2 != 2) {
                        v23Var.j0();
                    } else {
                        String strL = v23Var.L();
                        if (strL != null) {
                            int iHashCode = strL.hashCode();
                            if (iHashCode != 98) {
                                if (iHashCode != 117) {
                                    if (iHashCode == 3169 && strL.equals("cd")) {
                                        zF = v23Var.F();
                                    }
                                } else if (strL.equals("u")) {
                                    strW2 = v23Var.W();
                                    yz2.i(strW2, "nextString(...)");
                                }
                            } else if (strL.equals("b")) {
                                strW = v23Var.W();
                                yz2.i(strW, "nextString(...)");
                            }
                        }
                        v23Var.j0();
                    }
                }
            }

            @Override // com.google.gson.b
            public final void c(a33 a33Var, Object obj) throws IOException {
                cg0 cg0Var = (cg0) obj;
                yz2.j(a33Var, "out");
                if (cg0Var == null) {
                    a33Var.u();
                    return;
                }
                a33Var.f();
                a33Var.p("b");
                a33Var.P(cg0Var.a);
                a33Var.p("u");
                a33Var.P(cg0Var.b);
                if (cg0Var.c) {
                    a33Var.p("cd");
                    a33Var.S(true);
                }
                a33Var.o();
            }
        };
        bm2 bm2Var = new bm2();
        bm2Var.b(cg0.class, bVar);
        h = bm2Var.a();
        Duration durationOfSeconds = Duration.ofSeconds(600L);
        yz2.i(durationOfSeconds, "ofSeconds(...)");
        i = new qi0(durationOfSeconds, new i3(18));
        cg0 cg0Var = new cg0("com.nationaledtech.spinbrowser", "mozac_browser_toolbar_url_view", true);
        cg0 cg0Var2 = new cg0("alook.browser", "search_fragment_input_view", false);
        cg0 cg0Var3 = new cg0("alook.browser.google", "search_fragment_input_view", false);
        cg0 cg0Var4 = new cg0("app.vanadium.browser", "url_bar", false);
        cg0 cg0Var5 = new cg0("com.amazon.cloud9", "url", false);
        cg0 cg0Var6 = new cg0("com.android.browser", "url", false);
        cg0 cg0Var7 = new cg0("com.android.chrome", "origin,url_bar", false);
        cg0 cg0Var8 = new cg0("com.avast.android.secure.browser", "editor", false);
        cg0 cg0Var9 = new cg0("com.avg.android.secure.browser", "editor", false);
        cg0 cg0Var10 = new cg0("com.brave.browser", "url_bar", false);
        cg0 cg0Var11 = new cg0("com.brave.browser_beta", "url_bar", false);
        cg0 cg0Var12 = new cg0("com.brave.browser_default", "url_bar", false);
        cg0 cg0Var13 = new cg0("com.brave.browser_dev", "url_bar", false);
        cg0 cg0Var14 = new cg0("com.brave.browser_nightly", "url_bar", false);
        cg0 cg0Var15 = new cg0("com.chrome.beta", "origin,url_bar", false);
        cg0 cg0Var16 = new cg0("com.chrome.canary", "origin,url_bar", false);
        cg0 cg0Var17 = new cg0("com.chrome.dev", "origin,url_bar", false);
        cg0 cg0Var18 = new cg0("com.cookiegames.smartcookie", "search", false);
        cg0 cg0Var19 = new cg0("com.cookiejarapps.android.smartcookieweb", "mozac_browser_toolbar_url_view", true);
        cg0 cg0Var20 = new cg0("com.duckduckgo.mobile.android", "omnibarTextInput", false);
        cg0 cg0Var21 = new cg0("com.ecosia.android", "url_bar", false);
        cg0 cg0Var22 = new cg0("com.google.android.apps.chrome", "origin,url_bar", false);
        cg0 cg0Var23 = new cg0("com.google.android.apps.chrome_dev", "origin,url_bar", false);
        cg0 cg0Var24 = new cg0("com.iode.firefox", "mozac_browser_toolbar_url_view", true);
        cg0 cg0Var25 = new cg0("com.jamal2367.styx", "search", false);
        cg0 cg0Var26 = new cg0("com.kiwibrowser.browser", "url_bar", false);
        cg0 cg0Var27 = new cg0("com.kiwibrowser.browser.dev", "url_bar", false);
        cg0 cg0Var28 = new cg0("com.ktllq.play", "url_bar", false);
        cg0 cg0Var29 = new cg0("com.microsoft.emmx", "url_bar", false);
        cg0 cg0Var30 = new cg0("com.microsoft.emmx.beta", "url_bar", false);
        cg0 cg0Var31 = new cg0("com.microsoft.emmx.canary", "url_bar", false);
        cg0 cg0Var32 = new cg0("com.microsoft.emmx.dev", "url_bar", false);
        cg0 cg0Var33 = new cg0("com.mmbox.browser", "search_box", false);
        cg0 cg0Var34 = new cg0("com.mmbox.xbrowser", "search_box", false);
        cg0 cg0Var35 = new cg0("com.mycompany.app.soulbrowser", "edit_text", false);
        cg0 cg0Var36 = new cg0("com.naver.whale", "url_bar", false);
        cg0 cg0Var37 = new cg0("com.neeva.app", "full_url_text_view", false);
        cg0 cg0Var38 = new cg0("com.opera.browser", "url_field", false);
        cg0 cg0Var39 = new cg0("com.opera.browser.beta", "url_field", false);
        cg0 cg0Var40 = new cg0("com.opera.gx", "addressbarEdit", false);
        cg0 cg0Var41 = new cg0("com.opera.mini.native", "url_field", false);
        cg0 cg0Var42 = new cg0("com.opera.mini.native.beta", "url_field", false);
        cg0 cg0Var43 = new cg0("com.opera.touch", "addressbarEdit", false);
        cg0 cg0Var44 = new cg0("com.qflair.browserq", "url", false);
        cg0 cg0Var45 = new cg0("com.qwant.liberty", "mozac_browser_toolbar_url_view,url_bar_title", true);
        cg0 cg0Var46 = new cg0("com.rainsee.create", "search_box", false);
        cg0 cg0Var47 = new cg0("com.sec.android.app.sbrowser", "location_bar_edit_text", false);
        cg0 cg0Var48 = new cg0("com.sec.android.app.sbrowser.beta", "location_bar_edit_text", false);
        cg0 cg0Var49 = new cg0("com.stoutner.privacybrowser.free", "url_edittext", false);
        cg0 cg0Var50 = new cg0("com.stoutner.privacybrowser.standard", "url_edittext", false);
        cg0 cg0Var51 = new cg0("com.vivaldi.browser", "url_bar", false);
        cg0 cg0Var52 = new cg0("com.vivaldi.browser.snapshot", "url_bar", false);
        cg0 cg0Var53 = new cg0("com.vivaldi.browser.sopranos", "url_bar", false);
        cg0 cg0Var54 = new cg0("com.yandex.browser", "bro_omnibar_address_title_text,bro_omnibox_collapsed_title", false);
        cg0Var54.f = new w40(27);
        ArrayList arrayListU = us0.U(py1.x, vs0.p(cg0Var, cg0Var2, cg0Var3, cg0Var4, cg0Var5, cg0Var6, cg0Var7, cg0Var8, cg0Var9, cg0Var10, cg0Var11, cg0Var12, cg0Var13, cg0Var14, cg0Var15, cg0Var16, cg0Var17, cg0Var18, cg0Var19, cg0Var20, cg0Var21, cg0Var22, cg0Var23, cg0Var24, cg0Var25, cg0Var26, cg0Var27, cg0Var28, cg0Var29, cg0Var30, cg0Var31, cg0Var32, cg0Var33, cg0Var34, cg0Var35, cg0Var36, cg0Var37, cg0Var38, cg0Var39, cg0Var40, cg0Var41, cg0Var42, cg0Var43, cg0Var44, cg0Var45, cg0Var46, cg0Var47, cg0Var48, cg0Var49, cg0Var50, cg0Var51, cg0Var52, cg0Var53, cg0Var54, new cg0("com.yjllq.chrome.beta", "search_box", false), new cg0("com.yjllq.internet", "search_box", false), new cg0("com.yjllq.kito", "search_box", false), new cg0("com.yjllqint.kito", "search_box", false), new cg0("com.yujian.ResideMenuDemo", "search_box", false), new cg0("com.z28j.feel", "g2", false), new cg0("idm.internet.download.manager", "search", false), new cg0("idm.internet.download.manager.adm.lite", "search", false), new cg0("idm.internet.download.manager.plus", "search", false), new cg0("io.github.forkmaintainers.iceraven", "mozac_browser_toolbar_url_view", true), new cg0("mark.via", "am,an", false), new cg0("mark.via.gp", "as", false), new cg0("net.dezor.browser", "url_bar", false), new cg0("net.slions.fulguris.full.download", "search", false), new cg0("net.slions.fulguris.full.download.debug", "search", false), new cg0("net.slions.fulguris.full.playstore", "search", false), new cg0("net.slions.fulguris.full.playstore.debug", "search", false), new cg0("org.adblockplus.browser", "url_bar,url_bar_title", false), new cg0("org.adblockplus.browser.beta", "url_bar,url_bar_title", false), new cg0("org.bromite.bromite", "url_bar", false), new cg0("org.bromite.chromium", "url_bar", false), new cg0("org.chromium.chrome", "url_bar", false), new cg0("org.codeaurora.swe.browser", "url_bar", false), new cg0("org.cromite.cromite", "url_bar", false), new cg0("org.gnu.icecat", "url_bar_title,mozac_browser_toolbar_url_view", true), new cg0("org.ironfoxoss.ironfox", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.ironfoxoss.ironfox.nightly", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.mozilla.fenix", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view", true), new cg0("org.mozilla.fenix.nightly", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view", true), new cg0("org.mozilla.fennec_aurora", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.mozilla.fennec_fdroid", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.mozilla.firefox", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.mozilla.firefox_beta", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.mozilla.focus", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,display_url", true), new cg0("org.mozilla.focus.beta", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,display_url", true), new cg0("org.mozilla.focus.nightly", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,display_url", true), new cg0("org.mozilla.klar", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,display_url", true), new cg0("org.mozilla.reference.browser", "mozac_browser_toolbar_url_view", true), new cg0("org.mozilla.rocket", "display_url", false), new cg0("org.torproject.torbrowser", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.torproject.torbrowser_alpha", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("org.ungoogled.chromium.extensions.stable", "url_bar", false), new cg0("org.ungoogled.chromium.stable", "url_bar", false), new cg0("us.spotco.fennec_dos", "ADDRESSBAR_URL_BOX,mozac_browser_toolbar_url_view,url_bar_title", true), new cg0("acr.browser.barebones", "search", false), new cg0("acr.browser.lightning", "search", false), new cg0("com.feedback.browser.wjbrowser", "addressbar_url", false), new cg0("com.ghostery.android.ghostery", "search_field", false), new cg0("com.htc.sense.browser", "title", false), new cg0("com.jerky.browser2", "enterUrl", false), new cg0("com.ksmobile.cb", "address_bar_edit_text", false), new cg0("com.lemurbrowser.exts", "url_bar", false), new cg0("com.linkbubble.playstore", "url_text", false), new cg0("com.mx.browser", "address_editor_with_progress", false), new cg0("com.mx.browser.tablet", "address_editor_with_progress", false), new cg0("com.nubelacorp.javelin", "enterUrl", false), new cg0("com.zoho.primeum.stable", "url_bar", false), new cg0("jp.co.fenrir.android.sleipnir", "url_text", false), new cg0("jp.co.fenrir.android.sleipnir_black", "url_text", false), new cg0("jp.co.fenrir.android.sleipnir_test", "url_text", false), new cg0("mobi.mgeek.TunnyBrowser", "title", false), new cg0("org.iron.srware", "url_bar", false)));
        int iO = al3.o(vs0.e(arrayListU, 10));
        LinkedHashMap linkedHashMap = new LinkedHashMap(iO >= 16 ? iO : 16);
        int size = arrayListU.size();
        int i2 = 0;
        while (i2 < size) {
            Object obj = arrayListU.get(i2);
            i2++;
            linkedHashMap.put(((cg0) obj).a, obj);
        }
        j = linkedHashMap;
    }

    public cg0(String str, String str2, boolean z) {
        this.a = str;
        this.b = str2;
        this.c = z;
        Set setH0 = us0.h0(vv5.N(str2, new String[]{","}));
        this.d = setH0;
        Set<String> set = setH0;
        ArrayList arrayList = new ArrayList(vs0.e(set, 10));
        for (String str3 : set) {
            arrayList.add(this.a + ":id/" + str3);
        }
        this.e = arrayList;
        this.f = new w40(28);
    }

    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof cg0)) {
            return false;
        }
        cg0 cg0Var = (cg0) obj;
        return yz2.e(this.a, cg0Var.a) && yz2.e(this.b, cg0Var.b) && this.c == cg0Var.c;
    }

    public final int hashCode() {
        return ct3.i(this.b, this.a.hashCode() * 31, 31) + (this.c ? 1231 : 1237);
    }

    public final String toString() {
        StringBuilder sbY = ct3.y("BrowserConfig(browserPackage=", this.a, ", urlViewIds=", this.b, ", useRawViewIdFallback=");
        sbY.append(this.c);
        sbY.append(")");
        return sbY.toString();
    }
}
