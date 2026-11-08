"""AIR descriptor for the native account host; one runtime and cookie profile per account."""
from xml.sax.saxutils import escape
import uuid

VERSION='2.1.2'

def descriptor(require_activation=True):
    activation_metadata = '' if require_activation else '<meta-data android:name="local.huoying.air.requiresActivation" android:value="false"/>'
    metadata={'embeddedFonts':'false','namespaceVersion':'51.1','aspectRatio':'landscape','autoOrients':'false','fullScreen':'true','uniqueappversionid':str(uuid.uuid4()),'initialcontent':'MobileGame.swf','containsVideo':'false','webContentsDebuggingEnabled':'false','webViewAllowFileAccess':'false','disableMediaCodec':'false'}
    tags=''.join('<meta-data android:name="'+k+'" android:value="'+escape(v)+'"/>'for k,v in metadata.items())
    accounts=''.join(f'''<activity android:name="air.local.huoying.airgame.AccountEntries$Account{i}" android:process=":account{i}" android:exported="false" android:launchMode="singleTop" android:screenOrientation="landscape" android:configChanges="fontScale|keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize" android:windowSoftInputMode="adjustPan|stateHidden">{tags}</activity>
<provider android:name="local.huoying.air.auth.AccountBootstrap$Slot{i}" android:process=":account{i}" android:authorities="air.local.huoying.airgame.bootstrap{i}" android:exported="false"/><service android:name="local.huoying.air.auth.AirAccountActivity$Service{i}" android:process=":account{i}" android:exported="false" android:foregroundServiceType="specialUse"><property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Keep the user's actively opened game account connected until they stop it."/></service>'''for i in range(8))
    return f'''<?xml version="1.0" encoding="utf-8"?>
<application xmlns="http://ns.adobe.com/air/application/51.1">
<id>local.huoying.airgame</id><versionNumber>{VERSION}</versionNumber><filename>HuoyingOL</filename><name>火影OL</name>
<initialWindow><content>MobileGame.swf</content><visible>true</visible><width>1600</width><height>720</height><aspectRatio>landscape</aspectRatio><autoOrients>false</autoOrients><fullScreen>true</fullScreen><renderMode>cpu</renderMode></initialWindow>
<supportedProfiles>extendedDesktop desktop mobileDevice</supportedProfiles>
<extensions><extensionID>local.huoying.qqlogin</extensionID></extensions>
<android><webContentsDebuggingEnabled>false</webContentsDebuggingEnabled><buildLegacyAPK>true</buildLegacyAPK><manifestAdditions><![CDATA[
<manifest android:installLocation="auto"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.INTERNET"/><uses-permission android:name="android.permission.FOREGROUND_SERVICE"/><uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/><uses-permission android:name="android.permission.WAKE_LOCK"/><uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/><uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<queries><package android:name="com.tencent.mobileqq"/></queries>
<application android:enableOnBackInvokedCallback="true" android:allowBackup="false" android:hardwareAccelerated="true" android:usesCleartextTraffic="true">
{activation_metadata}
<activity android:name=".AppEntry" android:enabled="false"/>
<activity android:name="local.huoying.air.auth.AirProduct$Launcher" android:exported="false" android:theme="@android:style/Theme.Material.NoActionBar" android:screenOrientation="landscape"/><activity-alias android:name=".FireLauncher" android:targetActivity="local.huoying.air.auth.AirProduct$Launcher" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity-alias>
{accounts}
<provider android:name="local.huoying.air.auth.Diagnostics$Provider" android:authorities="air.local.huoying.airgame.diagnostics" android:exported="false" android:grantUriPermissions="true"/>
<activity android:name="local.huoying.air.auth.QqExtension$RestartActivity" android:process=":restart" android:exported="false" android:noHistory="true" android:screenOrientation="landscape" android:theme="@android:style/Theme.Material.NoActionBar"/>
<activity android:name="local.huoying.air.auth.QqExtension$ReturnActivity" android:exported="true" android:noHistory="true" android:launchMode="singleTop" android:excludeFromRecents="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
<intent-filter><action android:name="android.intent.action.VIEW"/><category android:name="android.intent.category.DEFAULT"/><category android:name="android.intent.category.BROWSABLE"/><data android:scheme="huoyingairqq" android:host="login"/></intent-filter>
<intent-filter android:autoVerify="true"><action android:name="android.intent.action.VIEW"/><category android:name="android.intent.category.DEFAULT"/><category android:name="android.intent.category.BROWSABLE"/><data android:scheme="https" android:host="ssl.ptlogin2.qq.com"/><uri-relative-filter-group><data android:path="/jump"/><data android:queryPrefix="air_huoying_state="/></uri-relative-filter-group></intent-filter>
</activity></application></manifest>]]></manifestAdditions></android></application>'''
