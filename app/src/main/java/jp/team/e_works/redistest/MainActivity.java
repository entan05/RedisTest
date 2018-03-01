package jp.team.e_works.redistest;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class MainActivity extends AppCompatActivity {
    // ログタグ
    private static final String TAG = "RedisTest";

    // デフォルトのRedis IP,Port
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6379;

    // Subscriber
    private JedisPubSub mSubscriber;

    // Redis IP, Port
    private String mHost;
    private int mPort = -1;

    // GUI
    private TextView mSubscribeText;
    private Switch mStartSwitch;
    private Button mSubmitButton;

    // Handler
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redis_test);

        // subscribe通知でUIをいじるためにHandlerを初期化
        mHandler = new Handler();

        // subscribe受信結果表示部
        mSubscribeText = (TextView)findViewById(R.id.subscribe_text);

        // publishのchannel, message指定
        final EditText channelText = (EditText)findViewById(R.id.channel_text);
        final EditText messageText = (EditText)findViewById(R.id.message_text);

        // publish button
        mSubmitButton = (Button)findViewById(R.id.submit_button);
        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // publish
                publish(channelText.getText().toString(), messageText.getText().toString());
                // publish後に入力を消す
                channelText.setText("");
                messageText.setText("");
            }
        });

        // RedisのIP, Port指定
        final EditText hostText = (EditText)findViewById(R.id.host_text);
        final EditText portText = (EditText)findViewById(R.id.port_text);
        // subscribe開始スイッチ
        mStartSwitch = (Switch)findViewById(R.id.start_switch);
        mStartSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    // IPの取得（指定がなければデフォルトに）
                    mHost = hostText.getText().toString();
                    if (TextUtils.isEmpty(mHost)) {
                        mHost = DEFAULT_HOST;
                    }
                    // Portの取得（不正な指定ならばデフォルトに）
                    try {
                        mPort = Integer.parseInt(portText.getText().toString());
                    } catch (NumberFormatException e) {
                        mPort = -1;
                    }
                    if (mPort < 0) {
                        mPort = DEFAULT_PORT;
                    }

                    // subscribe
                    mSubscriber = psubscribe("*");
                } else {
                    // unsubscribe
                    unsubscribe();
                }

                // GUI表示変更
                mSubmitButton.setEnabled(isChecked);
                mStartSwitch.setChecked(isChecked);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mStartSwitch.isChecked()) {
            // 画面を離れる際はunsubscribeしておく
            unsubscribe();
            mSubmitButton.setEnabled(false);
            mStartSwitch.setChecked(false);
        }
    }

    /**
     * 例外を文字列にする
     * @param e 文字列にする例外
     * @return 文字列にされた例外
     */
    private String exception2text(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString());
        sb.append("\n");
        for(StackTraceElement ste : e.getStackTrace()) {
            sb.append("    ");
            sb.append(ste.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * publishを行う
     * @param channel publish先のchannel
     * @param message publishするmessage
     */
    private void publish(final String channel, final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(!TextUtils.isEmpty(mHost) && mPort > 0) {
                    try {
                        // 指定したホスト、ポートでJedisオブジェクトを生成する
                        Jedis jedis = new Jedis(mHost, mPort);

                        // publish
                        jedis.publish(channel, message);

                        jedis.quit();
                    } catch (Exception e) {
                        Log.e(TAG, exception2text(e));
                    }
                }
            }
        }).start();
    }

    /**
     * subscribeを終了する
     */
    private void unsubscribe() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mSubscriber.unsubscribe();
                    mSubscriber.punsubscribe();
                } catch (Exception e) {
                    Log.e(TAG, exception2text(e));
                }
            }
        }).start();
    }

    /**
     * subscribeを開始する
     * @param channels 監視するchannel
     * @return subscriber
     */
    private JedisPubSub subscribe(String... channels) {
        return psubscribe(channels);
    }

    /**
     * psubscribeを開始する
     * @param patterns 監視するパターン
     * @return subscriber
     */
    private JedisPubSub psubscribe(final String... patterns) {
        // 引数がなければnullを返す
        if(patterns.length == 0) {
            return null;
        }

        final JedisPubSub jedisPubSub = new JedisPubSub() {
            // subscribeで受信した際に呼ばれる
            @Override
            public void onMessage(final String channel, final String message) {
                Log.i(TAG, "onMessage(channel:" + channel + ", message:" + message + ")");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSubscribeText.append("channel:" + channel + ", message:" + message + "\n");
                    }
                });
            }

            // psubscribeで受信した際に呼ばれる
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                Log.i(TAG, "onPMessage(pattern:" + pattern + ", channel:" + channel + ", message:" + message + ")");
                onMessage(channel, message);
            }

            // subscribe開始時に呼ばれる
            @Override
            public void onSubscribe(String channel, int subscribedChannels) {
                Log.i(TAG, "onSubscribe(channel:" + channel + ", subscribedChannels:" + subscribedChannels + ")");
            }

            // unsubscribe時に呼ばれる
            @Override
            public void onUnsubscribe(String channel, int subscribedChannels) {
                Log.i(TAG, "onUnsubscribe(channel:" + channel + ", subscribedChannels:" + subscribedChannels + ")");
            }

            // psubscribeに呼ばれる
            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {
                Log.i(TAG, "onPSubscribe(pattern:" + pattern + ", subscribedChannels:" + subscribedChannels + ")");
            }

            // punsubscribe時に呼ばれる
            @Override
            public void onPUnsubscribe(String pattern, int subscribedChannels) {
                Log.i(TAG, "onPUnsubscribe(pattern:" + pattern + ", subscribedChannels:" + subscribedChannels + ")");
            }
        };

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 指定したホスト、ポートでJedisオブジェクトを生成する
                    Jedis jedis = new Jedis(mHost, mPort);

                    jedis.psubscribe(jedisPubSub, patterns);

                    jedis.quit();
                } catch (Exception e) {
                    Log.e(TAG, exception2text(e));
                }
            }
        }).start();

        return jedisPubSub;
    }
}
