package com.jarvis.assistant;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout messages;
    private EditText input;
    private TextToSpeech tts;
    private String pendingNumber = null;
    private String pendingSms = null;

    private static final int VOICE = 10;
    private static final int CALL_PERMISSION = 11;
    private static final int SMS_PERMISSION = 12;
    private static final int CONTACT_PERMISSION = 13;
    private static final int CALENDAR_PERMISSION = 14;
    private static final int NOTIFICATION_PERMISSION = 15;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        tts = new TextToSpeech(this, s -> {});
        add("JARVIS", "JARVIS V2.2 online. I can use voice commands, call contacts, prepare SMS, open web search, create calendar events, set alarms, open apps, and connect to a secure AI backend.");
        handleWakeIntent(getIntent());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,20,20,12);

        TextView title = new TextView(this);
        title.setText("JARVIS V2");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1,70));

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(messages);
        root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout row = new LinearLayout(this);
        input = new EditText(this);
        input.setHint("Ask JARVIS...");
        row.addView(input,new LinearLayout.LayoutParams(0,-2,1));

        Button mic = new Button(this); mic.setText("🎙");
        mic.setOnClickListener(v -> voice());
        row.addView(mic);

        Button send = new Button(this); send.setText("Send");
        send.setOnClickListener(v -> process(input.getText().toString()));
        row.addView(send);

        root.addView(row);

        LinearLayout quick = new LinearLayout(this);
        Button call = new Button(this); call.setText("📞 Call");
        call.setOnClickListener(v -> process("call " + input.getText().toString()));
        quick.addView(call,new LinearLayout.LayoutParams(0,-2,1));
        Button sms = new Button(this); sms.setText("💬 SMS");
        sms.setOnClickListener(v -> add("JARVIS","Say: send message to NAME: MESSAGE"));
        quick.addView(sms,new LinearLayout.LayoutParams(0,-2,1));
        Button wake = new Button(this); wake.setText("🟢 Hey JARVIS");
        wake.setOnClickListener(v -> toggleWakeService());
        quick.addView(wake,new LinearLayout.LayoutParams(0,-2,1));
        Button web = new Button(this); web.setText("🌐 Web");
        web.setOnClickListener(v -> webSearch(input.getText().toString()));
        quick.addView(web,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(quick);

        LinearLayout wakeRow = new LinearLayout(this);
        Button stopWake = new Button(this); stopWake.setText("⏹ Stop Hey JARVIS");
        stopWake.setOnClickListener(v -> {
            stopService(new Intent(this, WakeWordService.class));
            add("JARVIS", "Hey JARVIS wake mode stopped.");
        });
        wakeRow.addView(stopWake,new LinearLayout.LayoutParams(-1,-2));
        root.addView(wakeRow);

        setContentView(root);
    }

    private void add(String who,String text){
        TextView t=new TextView(this);
        t.setText(who+": "+text);
        t.setTextSize(16);
        t.setPadding(12,10,12,10);
        messages.addView(t);
        messages.post(() -> ((ScrollView)messages.getParent()).fullScroll(View.FOCUS_DOWN));
    }

    private void voice(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},VOICE); return;
        }
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to JARVIS");
        startActivityForResult(i,VOICE);
    }

    private void toggleWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
            return;
        }
        Intent i = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        add("JARVIS", "Hey JARVIS wake mode enabled. You can lock the phone; Android will keep the microphone service running while permitted.");
        speak("Hey JARVIS is ready.");
    }

    private void handleWakeIntent(Intent intent) {
        if (intent == null) return;
        String command = intent.getStringExtra("WAKE_COMMAND");
        if (command != null && !command.trim().isEmpty()) {
            intent.removeExtra("WAKE_COMMAND");
            input.setText(command);
            process(command);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWakeIntent(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == VOICE || requestCode == NOTIFICATION_PERMISSION) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == VOICE) {
                // Permission was requested by the wake button; start wake mode now.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    toggleWakeService();
                }
            } else if (requestCode == NOTIFICATION_PERMISSION) {
                toggleWakeService();
            }
        }
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==VOICE && c==RESULT_OK && d!=null){
            ArrayList<String> a=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(a!=null&&!a.isEmpty()){input.setText(a.get(0)); process(a.get(0));}
        }
    }

    private void process(String raw){
        if(raw==null)return;
        String q=raw.trim();
        if(q.isEmpty())return;
        add("You",q);
        input.setText("");
        String l=q.toLowerCase(Locale.ROOT);

        if(l.startsWith("call ") || l.contains(" call ")){
            String name=q.substring(Math.max(0,l.indexOf("call ")+5)).trim();
            callContact(name); return;
        }

        if(l.startsWith("send message to ") || l.startsWith("send sms to ")){
            String prefix=l.startsWith("send sms to ") ? "send sms to " : "send message to ";
            int p=l.indexOf(":");
            if(p>0){
                String name=q.substring(prefix.length(),p).trim();
                String msg=q.substring(p+1).trim();
                smsContact(name,msg);
            } else add("JARVIS","Use: send message to Rahul: I will reach at 7 PM");
            return;
        }

        if(l.startsWith("search ") || l.startsWith("web search ")){
            String term=q.substring(l.startsWith("search ")?7:11).trim();
            webSearch(term); return;
        }

        if(l.startsWith("set alarm ")){
            String time=q.substring(10).trim();
            setAlarm(time); return;
        }

        if(l.startsWith("open ")){
            openApp(q.substring(5).trim()); return;
        }

        if(l.startsWith("calendar ") || l.startsWith("add calendar ")){
            createCalendar(q); return;
        }

        String reply="JARVIS V2 received your request. Connect a secure AI backend to enable live AI answers for general questions, documents and images.";
        add("JARVIS",reply);
        speak(reply);
    }

    private void callContact(String name){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},CONTACT_PERMISSION); return;
        }
        String num=findNumber(name);
        if(num==null){add("JARVIS","I couldn't find "+name+" in your contacts.");return;}
        pendingNumber=num;
        new AlertDialog.Builder(this).setTitle("Confirm call")
            .setMessage("Call "+name+" at "+num+"?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Call",(d,w)->doCall(num)).show();
    }

    private void doCall(String num){
        if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE},CALL_PERMISSION); return;
        }
        Intent i=new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+num));
        startActivity(i);
    }

    private void smsContact(String name,String msg){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},CONTACT_PERMISSION); return;
        }
        String num=findNumber(name);
        if(num==null){add("JARVIS","I couldn't find "+name+" in your contacts.");return;}
        pendingNumber=num; pendingSms=msg;
        new AlertDialog.Builder(this).setTitle("Confirm SMS")
            .setMessage("Send to "+name+":\n\n"+msg)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Send",(d,w)->doSms(num,msg)).show();
    }

    private void doSms(String num,String msg){
        if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.SEND_SMS},SMS_PERMISSION); return;
        }
        android.telephony.SmsManager.getDefault().sendTextMessage(num,null,msg,null,null);
        add("JARVIS","SMS sent.");
        speak("SMS sent.");
    }

    private String findNumber(String name){
        Cursor c=getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" LIKE ?",
            new String[]{"%"+name+"%"},
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");
        try{
            if(c!=null&&c.moveToFirst()) return c.getString(0);
        } finally {if(c!=null)c.close();}
        return null;
    }

    private void webSearch(String term){
        if(term==null||term.trim().isEmpty())return;
        Intent i=new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra("query",term);
        try{startActivity(i);}
        catch(Exception e){
            Intent b=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(term)));
            startActivity(b);
        }
    }

    private void setAlarm(String text){
        Intent i=new Intent(android.provider.AlarmClock.ACTION_SET_ALARM);
        i.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,"JARVIS reminder");
        i.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI,false);
        try{startActivity(i); add("JARVIS","Opening the alarm screen. Set the time there.");}
        catch(Exception e){add("JARVIS","Your phone does not provide an alarm app.");}
    }

    private void openApp(String name){
        String pkg=null;
        String l=name.toLowerCase(Locale.ROOT);
        if(l.contains("whatsapp"))pkg="com.whatsapp";
        else if(l.contains("youtube"))pkg="com.google.android.youtube";
        else if(l.contains("chrome"))pkg="com.android.chrome";
        if(pkg!=null){
            Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
            if(i!=null){startActivity(i);return;}
        }
        add("JARVIS","I don't have a launcher mapping for "+name+" yet.");
    }

    private void createCalendar(String q){
        Intent i=new Intent(Intent.ACTION_INSERT);
        i.setData(CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.Events.TITLE,q);
        try{startActivity(i);add("JARVIS","Opening Calendar so you can confirm the event.");}
        catch(Exception e){add("JARVIS","Calendar is not available.");}
    }

    private void speak(String s){
        if(tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
    }

    @Override protected void onDestroy(){
        if(tts!=null){tts.stop();tts.shutdown();}
        super.onDestroy();
    }
}
