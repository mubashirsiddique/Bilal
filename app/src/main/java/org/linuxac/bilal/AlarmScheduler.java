/*
 *  Copyright © 2017 Djalel Chefrour
 *
 *  This file is part of Bilal.
 *
 *  Bilal is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Bilal is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Bilal.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.linuxac.bilal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.util.Log;

import org.arabeyes.prayertime.Method;
import org.arabeyes.prayertime.PTLocation;
import org.arabeyes.prayertime.Prayer;
import org.arabeyes.prayertime.PrayerTime;
import org.linuxac.bilal.helpers.PrayerTimes;
import org.linuxac.bilal.helpers.UserSettings;
import org.linuxac.bilal.receivers.AlarmReceiver;
import org.linuxac.bilal.receivers.BootAndTimeChangeReceiver;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static android.content.Context.ALARM_SERVICE;

public class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";

    private static boolean sLocationIsSet = false;    // TODO rm as it's a setting
    public static String sCityName = "Souk Ahras";
    private static PTLocation sPTLocation = new PTLocation(36.28639, 7.95111, 1, 0, 697, 1010, 10);
    // http://dateandtime.info/citycoordinates.php?id=2479215

    private static Method sCalculationMethod;   // TODO rm as it's a setting
    static {
        sCalculationMethod = new Method();
        sCalculationMethod.setMethod(Method.MUSLIM_LEAGUE);
        sCalculationMethod.round = 0;
        sLocationIsSet = true;
    }

    private static final GregorianCalendar sLongLongTimeAgo = new GregorianCalendar(0,0,0);
    private static GregorianCalendar sLastTime = sLongLongTimeAgo;
    private static PrayerTimes sPrayerTimes = null;

    public static final String ALARM_TXT = "org.linuxac.bilal.ALARM_TXT";
    private static PendingIntent sAlarmIntent = null;

    private AlarmScheduler() {}

    public static boolean prayerTimesNotAvailable()
    {
        return null == sPrayerTimes;
    }

    public static GregorianCalendar getCurrentPrayer()
    {
        if (BuildConfig.DEBUG && null == sPrayerTimes) {
            Log.w(TAG, "sPrayerTimes == null");
            return null;
        }
        return sPrayerTimes.getCurrent();
    }

    public static int getCurrentPrayerIndex()
    {
        if (BuildConfig.DEBUG && null == sPrayerTimes) {
            Log.w(TAG, "sPrayerTimes == null");
            return -1;
        }
        return sPrayerTimes.getCurrentIndex();
    }

    public static GregorianCalendar getNextPrayer()
    {
        if (BuildConfig.DEBUG && null == sPrayerTimes) {
            Log.w(TAG, "sPrayerTimes == null");
            return null;
        }
        return sPrayerTimes.getNext();
    }

    public static int getNextPrayerIndex()
    {
        if (BuildConfig.DEBUG && null == sPrayerTimes) {
            Log.w(TAG, "sPrayerTimes == null");
            return -1;
        }
        return sPrayerTimes.getNextIndex();
    }

    public static String formatPrayer(int i)
    {
        if (BuildConfig.DEBUG && null == sPrayerTimes) {
            Log.w(TAG, "sPrayerTimes == null");
            return "";
        }
        return sPrayerTimes.format(i);
    }
    public static void enableAlarm(Context context)
    {
        Log.d(TAG, "Enabling Alarm.");
        enableBootAndTimeChangeReceiver(context);
        updatePrayerTimes(context, true);
    }

    public static void disableAlarm(Context context)
    {
        Log.d(TAG, "Disabling Alarm.");
        cancelAlarm(context);
        disableBootAndTimeChangeReceiver(context);
    }
/*

    private static void logBootAndTimeChangeReceiverSetting(Context context)
    {
        ComponentName receiver = new ComponentName(context, BootAndTimeChangeReceiver.class);
        PackageManager pm = context.getPackageManager();
        Log.d(TAG, "BootAndTimeChangeReceiver Setting = " +
                pm.getComponentEnabledSetting(receiver));
    }
*/

    private static void enableBootAndTimeChangeReceiver(Context context)
    {
        ComponentName receiver = new ComponentName(context, BootAndTimeChangeReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static void disableBootAndTimeChangeReceiver(Context context)
    {
        ComponentName receiver = new ComponentName(context, BootAndTimeChangeReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }


    private static boolean sameDay(GregorianCalendar a, GregorianCalendar b)
    {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
                a.get(Calendar.DATE) == b.get(Calendar.DATE);
    }


    /*
     * Here be dragons. This is an app entry point. All bets are off!
     * See comment in MainActivity.onCreate()
     */
    public static void updatePrayerTimes(Context context, boolean enableAlarm)
    {
        Log.i(TAG, "updatePrayerTimes(..., " + enableAlarm + ")");

//        logBootAndTimeChangeReceiverSetting(context);

        // TODO UserSettings.getLocation
        // TODO UserSettings.getMethod
        if (!sLocationIsSet) {
            Log.w(TAG, "Location not set! Nothing todo until user starts UI.");
            return;
        }

        // In SettingsActivity, listener calls us before setting is committed to shared prefs.
        boolean setAlarm = enableAlarm || UserSettings.isAlarmEnabled(context);
        GregorianCalendar nowCal = new GregorianCalendar();
        if (sameDay(sLastTime, nowCal)) {
            int oldNext = sPrayerTimes.getNextIndex();
            sPrayerTimes.updateCurrent(nowCal);
            if (setAlarm && !enableAlarm && oldNext == sPrayerTimes.getNextIndex()) {
                setAlarm = false;
                Log.i(TAG, "Keep old alarm.");
            }
            Log.d(TAG, "Call it a day :)");
        }
        else {
            Log.d(TAG, "Last time: " + PrayerTimes.format(sLastTime));
            GregorianCalendar[] ptCal = getPrayerTimes(context, nowCal);
            sPrayerTimes = new PrayerTimes(nowCal, ptCal);
            sLastTime = nowCal;
        }

        Log.d(TAG, "Current time: " + PrayerTimes.format(nowCal));
        Log.d(TAG, "Current prayer: " + sPrayerTimes.getCurrentName(context));
        Log.i(TAG, "Next prayer: " + sPrayerTimes.getNextName(context));

        if (setAlarm) {
            scheduleAlarm(context);
        }
    }

    @NonNull
    private static GregorianCalendar[] getPrayerTimes(Context context, GregorianCalendar nowCal)
    {
        int i;
        Prayer prayer = new Prayer();
        Date today = nowCal.getTime();
        GregorianCalendar[] ptCal = new GregorianCalendar[Prayer.NB_PRAYERS + 1];

        /* Call the main library function to fill the Prayer times */
        PrayerTime[] pt = prayer.getPrayerTimes(sPTLocation, sCalculationMethod, today);
        for (i = 0; i < Prayer.NB_PRAYERS; i++) {
            ptCal[i] = (GregorianCalendar)nowCal.clone();
            ptCal[i].set(Calendar.HOUR_OF_DAY, pt[i].hour);
            ptCal[i].set(Calendar.MINUTE, pt[i].minute);
            ptCal[i].set(Calendar.SECOND, pt[i].second);
            Log.d(TAG, PrayerTimes.getName(context, i) + " " + PrayerTimes.format(ptCal[i]));
        }

        PrayerTime nextPT = prayer.getNextDayFajr(sPTLocation, sCalculationMethod, today);
        ptCal[i] = (GregorianCalendar)nowCal.clone();
        ptCal[i].add(Calendar.DATE, 1);
        ptCal[i].set(Calendar.HOUR_OF_DAY, nextPT.hour);
        ptCal[i].set(Calendar.MINUTE, nextPT.minute);
        ptCal[i].set(Calendar.SECOND, nextPT.second);
        Log.d(TAG, context.getString(R.string.nextfajr) + " " + PrayerTimes.format(ptCal[i]));

        return ptCal;
    }

    private static PendingIntent createAlarmIntent(Context context)
    {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_PRAYER_INDEX, "" + sPrayerTimes.getNextIndex());
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private static void cancelAlarm(Context context)
    {
        if (null != sAlarmIntent) {
            sAlarmIntent.cancel();
        }
        else {
            sAlarmIntent = createAlarmIntent(context);
        }
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        alarmMgr.cancel(sAlarmIntent);
        sAlarmIntent = null;
        Log.i(TAG, "Old alarm cancelled.");
    }

    private static void scheduleAlarm(Context context)
    {
        sAlarmIntent = createAlarmIntent(context);
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        alarmMgr.set(AlarmManager.RTC_WAKEUP, sPrayerTimes.getNext().getTimeInMillis(), sAlarmIntent);
        Log.i(TAG, "New Alarm set for " + sPrayerTimes.format(sPrayerTimes.getNextIndex()));
    }
}