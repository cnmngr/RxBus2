package com.michaelflisar.rxbus2.demo;

import android.os.Bundle;
import android.util.Log;

import com.michaelflisar.rxbus2.RxBus;
import com.michaelflisar.rxbus2.RxBusBuilder;
import com.michaelflisar.rxbus2.demo.classes.TestEvent;
import com.michaelflisar.rxbus2.rx.RxBusMode;
import com.michaelflisar.rxbus2.rx.RxDisposableManager;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * Created by flisar on 28.04.2016.
 */
public class DemoActivity extends PauseAwareActivity
{
    private static final String TAG = "RxBus2 - " + DemoActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        testGeneral();
        testWithKeys();
        testAdvanced();
        testAdvancedWithCast();

        // -----------------
        // Send some events
        // -----------------

        // lets send some sync events
        for (int i = 0; i < 5; i++)
            RxBus.get().send(getLogMessage("onCreate", "main thread i=" + i));

        // lets say another thread is currently emitting events => send some async events
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Log.d(TAG, "Thread startet...");
                for (int i = 0; i < 5; i++)
                {
                    RxBus.get().send(getLogMessage("onCreate", "some thread i=" + i));
                }
            }
        }).start();

        // lets send some events bound to a key (can be a string or an integer)
        // 1 loop: sends events to the given key ONLY
        // 2 loop: sends events to all observers of the key AND to all simple String event observer
        for (int i = 0; i < 5; i++)
        {
            RxBus.get()
                    .withKey(R.id.custom_event_id_1)
                    .send(getLogMessage("onCreate", "KEY 1 main thread i=" + i));
        }
        for (int i = 0; i < 5; i++)
        {
            RxBus.get()
                    .withKey(R.id.custom_event_id_2).withSendToDefaultBus()
                    .send(getLogMessage("onCreate", "KEY 2 (AND ALL String listeners) main thread i=" + i));
        }

        // -----------------
        // Send some advanced events
        // -----------------

        // lets send some TestEvent and sub class events and check, if the listener of TestEvent receives the sub classes as well
        // => we achieve that via the cast operator!
        // without the cast operator, only concrete class observers will receive the event!
        RxBus.get().send(new TestEvent());
        RxBus.get()
                .withCast(TestEvent.class)
                .send(new TestEvent.TestSubEvent1());
        RxBus.get()
                .withCast(TestEvent.class)
                .send(new TestEvent.TestSubEvent2());
        // alternatively we can do following, this will send the event to all super class observers as well
        // and this setting can be enabled by default via RxBusDefauls.get().setSendToSuperClassesAsWell(true) if desired
        RxBus.get()
                .withSendToSuperClasses(true)
                .send(new TestEvent.TestSubEvent3());

        // this event won't be received by anyone
        RxBus.get()
                .send(new TestEvent.TestSubEvent3());

        RxBus.get()
                .send(new TestEvent.TestSubEvent4());

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }

                RxBus.get().send(new TestEvent().withText("from thread"));
                RxBus.get()
                        .withCast(TestEvent.class)
                        .send(new TestEvent.TestSubEvent1().withText("from thread"));
                RxBus.get()
                        .withCast(TestEvent.class)
                        .send(new TestEvent.TestSubEvent2().withText("from thread"));

                RxBus.get()
                        .withSendToSuperClasses(true)
                        .send(new TestEvent.TestSubEvent3().withText("from thread"));

                RxBus.get()
                        .send(new TestEvent.TestSubEvent4().withText("from thread"));
            }
        }).start();
    }

    @Override
    public void onPause()
    {
        RxBus.get().send(getLogMessage("onPause", "BEFORE on pause"));
        Log.d(TAG, "ACTIVITY BEFORE PAUSED");
        super.onPause();
        Log.d(TAG, "ACTIVITY AFTER PAUSED");
        RxBus.get().send(getLogMessage("onPause", "AFTER on pause"));
    }

    @Override
    public void onResume()
    {
        RxBus.get().send(getLogMessage("onResume", "BEFORE on resume"));
        Log.d(TAG, "ACTIVITY BEFORE RESUMED");
        super.onResume();
        Log.d(TAG, "ACTIVITY AFTER RESUMED");
        RxBus.get().send(getLogMessage("onResume", "AFTER on resume"));
    }

    @Override
    public void onDestroy()
    {
        // unsubscribe - we used the RxDisposableManager for every disposable and bound all disposables to this class,
        // so following will safely unsubscribe every disposable
        RxDisposableManager.unsubscribe(this);
        super.onDestroy();
    }

    // -----------------------------
    // Logging
    // -----------------------------

    private String getLogMessage(String method, String msg)
    {
        return "[" + method + "] {" + Thread.currentThread().getName() + "} : " + msg;
    }

    private void logEvent(String event, boolean queuedBus, String key, String extra)
    {
        Log.d(TAG, String.format("Type: %s%s (key=%s), Event: %s", queuedBus ? "QUEUED BUS" : "SIMPLE BUS", extra != null ? extra : "", key == null ? "NONE" : key, event));
    }

    // -----------------------------
    // Tests
    // -----------------------------

    private void testGeneral()
    {
        // 1) Just subscribe to a bus event => use the builders subscribe overload for this!
        Disposable disposableManual = RxBusBuilder.create(String.class)
                .subscribe(new Consumer<String>(){
                    @Override
                    public void accept(String s) {
                        logEvent(s, false, null, null);
                    }
                });
        // ATTENTION: this disposable MUST be handled by you, unsubscribe whenever you want!
        // Currently it will leak the Activity!!!

        // 2) Subscribe to an event and let RxDisposableManager manage your disposable - you just need to call
        // RxDisposableManager.unsubscribe(boundObject); to unsubscribe ALL disposables for a bound object
        // additionally this here enablea queuing + emits items on the main thread
        RxBusBuilder.create(String.class)
                .withQueuing(this)          // optional: if enabled, events will be queued while the IRxBusQueue is paused!
                .withBound(this)            // optional: this binds the subcritpion to this object and you can unsubscribe all bound disposables at once
                .withMode(RxBusMode.Main)   // optional: set the thread to main or background if wanted, events will be emitted on the corresponding thread
                .subscribe(new Consumer<String>(){
                    @Override
                    public void accept(String s) {
                        logEvent(s, true, null, null);
                    }
                });

        // 3) Get a simple Flowable and do whatever you want with it
        // all RxBus options like queuing and keys are available here as well!!!
        Flowable<String> flowable = RxBusBuilder.create(String.class)
                // optional:
//                .withQueuing(this)
//                .withKey(...)
                .build();
        // do something with this Flowable...
    }

    private void testWithKeys()
    {
        // you can use everything that is shown in testGeneral here as well, example will not show all possible combinations!

        // 1) Subscribe to a string event and only listen to a special key (+ queuing is enabled as well)
        // Disposable is managed automatically as well by RxDisposableManager
        RxBusBuilder.create(String.class)
                // all optional!!!
                .withQueuing(this)
                .withBound(this)
                .withKey(R.id.custom_event_id_1) // you may add multiple keys as well!
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<String>(){
                    @Override
                    public void accept(String s) {
                        logEvent(s, true, "custom_event_id_1", null);
                    }
                });

        RxBusBuilder.create(String.class)
                // all optional!!!
                .withQueuing(this)
                .withBound(this)
                .withKey(R.id.custom_event_id_2) // you may add multiple keys as well!
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<String>(){
                    @Override
                    public void accept(String s) {
                        logEvent(s, true, "custom_event_id_2", null);
                    }
                });

        Flowable<String> flowable = RxBusBuilder.create(String.class)
                .withQueuing(this)
                .withKey(R.id.custom_event_id_1) // you may add multiple keys as well!
                .build();
    }

    private void testAdvanced()
    {
        // 1) subscribe to a string event but emit integers => just pass in a transformer to the subcribe function!
        RxBusBuilder.create(String.class)
                .withQueuing(this)
                .withBound(this)
                .withKey(R.id.custom_event_id_1) // you may add multiple keys as well!
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer s) {
                        logEvent("Hash value: " + s.toString(), true, "custom_event_id_1", " [TRANSFORMED to HASH]");
                    }
                }, new FlowableTransformer<String, Integer>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<String> flowable) {
                        return flowable
                                .map(new Function<String, Integer>() {
                                    @Override
                                    public Integer apply(String s) {
                                        return s.hashCode();
                                    }
                                });
                    }
                });

        // 2) You need more control or dont want to use the transformer to compose a new Flowable? Then create an Flowable only and do the rest yourself!
        Flowable<String> flowable = RxBusBuilder.create(String.class)
                .withQueuing(this)
                .withKey(R.id.custom_event_id_1) // you may add multiple keys as well!
                .build();

        // do whatever youn want with the flowable
        Flowable result = flowable
//                ....
//                .toList(...)
//                .flatMap(...)
//                .map(...)
        ;
        Disposable disposable = result.subscribe(new Consumer() {
            @Override
            public void accept(Object o) {
                // ...
            }
        });
        // Don't forget to manage the subcription!! If you want you can use the RxDisposableManager manually here:
        RxDisposableManager.addDisposable(this, disposable);
    }

    private void testAdvancedWithCast()
    {
        // subscribe to TestEvent event
        RxBusBuilder.create(TestEvent.class)
                .withQueuing(this)
                .withBound(this)
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<TestEvent>() {
                    @Override
                    public void accept(TestEvent s) {
                        logEvent("TEST - BASE CLASS and QUEUED - " + TestEvent.class.getSimpleName(), true, null, " [ActualClass: " + s.toString() + "]");
                    }
                });

        // explicitly subscribe to TestEvent.TestSubEvent1 event only
        RxBusBuilder.create(TestEvent.TestSubEvent4.class)
                .withQueuing(this)
                .withBound(this)
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<TestEvent.TestSubEvent4>() {
                    @Override
                    public void accept(TestEvent.TestSubEvent4 s) {
                        logEvent("TEST - SUBCLASS4 and QUEUED - " + s.getClass().getSimpleName(), true, null, " [ActualClass: " + s.toString() + "]");
                    }
                });

        // subscribe to TestEvent event - NO queuing
        RxBusBuilder.create(TestEvent.class)
                .withBound(this)
                .withMode(RxBusMode.Main)
                .subscribe(new Consumer<TestEvent>() {
                    @Override
                    public void accept(TestEvent s) {
                        logEvent("TEST - BASE CLASS and not queued - " + TestEvent.class.getSimpleName(), false, null, " [ActualClass: " + s.toString() + "]");
                    }
                });
    }
}