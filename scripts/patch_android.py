#!/usr/bin/env python3
"""Run AFTER `npx cap add android` (CI does it for you). Adds the ForgeBar Kotlin plugin:
 - copies native/com/forge/bar/*.kt
 - adds manifest permissions + the health foreground service
 - registers the plugin in MainActivity
 - enables Kotlin in Gradle (only if the project does not already have it)"""
import re,glob,os,shutil,sys
A="android";root=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def rd(p):return open(p,encoding="utf-8").read()
def wr(p,s):open(p,"w",encoding="utf-8").write(s)

# 1. Kotlin sources
dst=f"{A}/app/src/main/java/com/forge/bar";os.makedirs(dst,exist_ok=True)
for f in glob.glob(f"{root}/native/com/forge/bar/*.kt"):shutil.copy(f,dst)

# 2. manifest
mp=f"{A}/app/src/main/AndroidManifest.xml";m=rd(mp)
perms=['android.permission.ACTIVITY_RECOGNITION','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_HEALTH','android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED']
add="".join(f'\n    <uses-permission android:name="{p}" />' for p in perms if p not in m)
if 'sensor.stepcounter' not in m:add+='\n    <uses-feature android:name="android.hardware.sensor.stepcounter" android:required="false" />'
m=m.replace("</manifest>",add+"\n</manifest>",1) if add else m
if "com.forge.bar.StepService" not in m:
    m=m.replace("</application>",'    <service android:name="com.forge.bar.StepService" android:exported="false" android:foregroundServiceType="health" />\n    </application>',1)
if "com.forge.bar.BootReceiver" not in m:
    m=m.replace("</application>",'    <receiver android:name="com.forge.bar.BootReceiver" android:exported="false"><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter></receiver>\n    </application>',1)
wr(mp,m)

# 3. MainActivity (Capacitor template is Java)
mains=glob.glob(f"{A}/app/src/main/java/**/MainActivity.*",recursive=True)
if not mains:sys.exit("MainActivity not found")
mf=mains[0];s=rd(mf)
if "ForgeBarPlugin" not in s:
    pkg=re.search(r"package\s+([\w.]+);",s).group(1)
    wr(mf,f"""package {pkg};

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.forge.bar.ForgeBarPlugin;

public class MainActivity extends BridgeActivity {{
    @Override
    public void onCreate(Bundle savedInstanceState) {{
        registerPlugin(ForgeBarPlugin.class);   // must be before super.onCreate
        super.onCreate(savedInstanceState);
    }}
}}
""")

# 4. Kotlin in Gradle
rb=f"{A}/build.gradle";r=rd(rb)
if "kotlin-gradle-plugin" not in r:
    r=re.sub(r"(dependencies\s*\{)",r"\1\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20'",r,count=1);wr(rb,r)
ab=f"{A}/app/build.gradle";a=rd(ab)
if "kotlin-android" not in a:
    a=a.replace("apply plugin: 'com.android.application'","apply plugin: 'com.android.application'\napply plugin: 'kotlin-android'",1)
    a+="\nkotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21 } }\n";wr(ab,a)
print("ForgeBar plugin installed")
