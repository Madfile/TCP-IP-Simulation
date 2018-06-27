package stp;




import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 *  timer for retransmission
 */
public class SenderTimer {

    private Timer senderTimer;
    private Sender sender = null;
    private int timeout_value = 0;
    private int count_value = 0;
    private TimerTask senderTimerTask;

    public SenderTimer(Sender sender, int timeout) {
        this.sender = sender;
        senderTimer = new Timer();
        this.timeout_value = timeout;
    }

    /**
     * when time's up, act retransmit
     */
    public void actRetransmit() {
        try {
            sender.retransmit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * give timeout to parameter
     */
        public void setCount() {
        this.count_value = timeout_value;
    }


    /**
     * reset the timer
     */
    public void set() {
        try {
            senderTimerTask.cancel();
            senderTimer.purge();
        } catch (IllegalStateException e) {

        } catch (NullPointerException e) {

        }
        setCount();
        senderTimerTask = new TimerTask() {
            @Override
            public void run() {
                count_value--;
                switch(count_value) {
                case 0:
                		actRetransmit();
                default:
                		break;
                }
            }
        };
        senderTimer.scheduleAtFixedRate(senderTimerTask, 0, 1);
    }
}
