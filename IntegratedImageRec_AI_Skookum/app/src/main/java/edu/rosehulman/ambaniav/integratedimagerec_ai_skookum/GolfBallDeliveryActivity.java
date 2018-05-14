package edu.rosehulman.ambaniav.integratedimagerec_ai_skookum;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import me435.NavUtils;

public class GolfBallDeliveryActivity extends ImageRecActivity {

    /**
     * Constant used with logging that you'll see later.
     */
    public static final String TAG = "GolfBallDelivery";
    private static final int LEFT_PWM_VALUE_FOR_STRAIGHT = 245;
    private static final int RIGHT_PWM_VALUE_FOR_STRAIGHT=255;
    private static final int LEFT_PWM_VALUE_FOR_TURN = 170;
    private static final int RIGHT_PWM_VALUE_FOR_TURN = 180;


    private static final int LOWEST_DESIRABLE_DUTY_CYCLE=100;

    private static final double MAX_SIZE_PERCENTAGE = 0.07; // Criteria to drop the ball
    private static final double LEFT_PROPORTIONAL_CONTROL = 10;
    private static final double RIGHT_PROPORTIONAL_CONTROL = 5;

    private TextView mTargetGPSTextView, mDistanceTextView;
    private RelativeLayout mMainLayout;

    // Go into Image recognition mode for that ball/mission, when the GPS is within the 20ft range
    // and a small cone has been detected (0.1% = 0.001)
    // If Image rec control takes robot 40ft or more away from cone, revert back to GPS control( Drive Towards)
    //


    public enum State {
        READY_FOR_MISSION,
        INITIAL_STRAIGHT,
        DRIVE_TOWARDS_NEAR_BALL,
        IMAGE_REC_NEAR,
        NEAR_BALL_SCRIPT,
        DRIVE_TOWARDS_FAR_BALL,
        IMAGE_REC_FAR,
        FAR_BALL_SCRIPT,
        DRIVE_TOWARDS_HOME,
        IMAGE_REC_HOME,
        WAITING_FOR_PICKUP,
        SEEKING_HOME,




    }
    public State mState;

    /**
     * An enum used for variables when a ball color needs to be referenced.
     */
    public enum BallColor {
        NONE, BLUE, RED, YELLOW, GREEN, BLACK, WHITE
    }


    public int farBall, nearBall, BlaWhi;

    /**
     * An array (of size 3) that stores what color is present in each golf ball stand location.
     */
    public BallColor[] mLocationColors = new BallColor[]{BallColor.NONE, BallColor.NONE, BallColor.NONE};

    /**
     * Simple boolean that is updated when the Team button is pressed to switch teams.
     */
    public boolean mOnRedTeam = false;


    // ---------------------- UI References ----------------------
    /**
     * An array (of size 3) that keeps a reference to the 3 balls displayed on the UI.
     */
    private ImageButton[] mBallImageButtons;

    /**
     * References to the buttons on the UI that can change color.
     */
    private Button mTeamChangeButton, mGoOrMissionCompleteButton, mJumboGoOrMissionCompleteButton;

    /**
     * An array constants (of size 7) that keeps a reference to the different ball color images resources.
     */
    // Note, the order is important and must be the same throughout the app.
    private static final int[] BALL_DRAWABLE_RESOURCES = new int[]{R.drawable.none_ball, R.drawable.blue_ball,
            R.drawable.red_ball, R.drawable.yellow_ball, R.drawable.green_ball, R.drawable.black_ball, R.drawable.white_ball};

    /**
     * TextViews that can change values.
     */
    private TextView mCurrentStateTextView, mStateTimeTextView, mGpsInfoTextView, mSensorOrientationTextView,
            mGuessXYTextView, mLeftDutyCycleTextView, mRightDutyCycleTextView, mMatchTimeTextView;

    private TextView mJumboXTextView, mJumboYTextView;

    protected LinearLayout mJumbotronLinearLayout;

    public boolean doImageRec=true;
    // ---------------------- End of UI References ----------------------


    // ---------------------- Mission strategy values ----------------------
    /**
     * Constants for the known locations.
     */
    public static final long NEAR_BALL_GPS_X = 90;
    public static final long FAR_BALL_GPS_X = 240;


    /**
     * Variables that will be either 50 or -50 depending on the balls we get.
     */
    private double mNearBallGpsY, mFarBallGpsY;

    /**
     * If that ball is present the values will be 1, 2, or 3.
     * If not present the value will be 0.
     * For example if we have the black ball, then mWhiteBallLocation will equal 0.
     */
    public int mNearBallLocation, mFarBallLocation, mWhiteBallLocation;
    // ----------------- End of mission strategy values ----------------------


    // ---------------------------- Timing area ------------------------------
    /**
     * Time when the state began (saved as the number of millisecond since epoch).
     */
    private long mStateStartTime;

    /**
     * Time when the match began, ie when Go! was pressed (saved as the number of millisecond since epoch).
     */
    private long mMatchStartTime;

    /**
     * Constant that holds the maximum length of the match (saved in milliseconds).
     */
    private long MATCH_LENGTH_MS = 300000; // 5 minutes in milliseconds (5 * 60 * 1000)
    // ----------------------- End of timing area --------------------------------


    // ---------------------------- Driving area ---------------------------------
    /**
     * When driving towards a target, using a seek strategy, consider that state a success when the
     * GPS distance to the target is less than (or equal to) this value.
     */
    public static final double ACCEPTED_DISTANCE_AWAY_FT = 10.0; // Within 10 feet is close enough.

    /**
     * Multiplier used during seeking to calculate a PWM value based on the turn amount needed.
     */
    private static final double SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER = 3.0;  // units are (PWM value)/degrees

    /**
     * Variable used to cap the slowest PWM duty cycle used while seeking. Pick a value from -255 to 255.
     */
    private static final int LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE = 150;

    /**
     * PWM duty cycle values used with the drive straight dialog that make your robot drive straightest.
     */
    public int mLeftStraightPwmValue = 240, mRightStraightPwmValue = 255;
    // ------------------------ End of Driving area ------------------------------

    private Scripts mScripts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBallImageButtons = new ImageButton[]{(ImageButton) findViewById(R.id.location_1_image_button),
                (ImageButton) findViewById(R.id.location_2_image_button),
                (ImageButton) findViewById(R.id.location_3_image_button)};
        mTeamChangeButton = (Button) findViewById(R.id.team_change_button);
        mCurrentStateTextView = (TextView) findViewById(R.id.current_state_textview);
        mStateTimeTextView = (TextView) findViewById(R.id.state_time_textview);
        mGpsInfoTextView = (TextView) findViewById(R.id.gps_info_textview);
        mSensorOrientationTextView = (TextView) findViewById(R.id.orientation_textview);
        mGuessXYTextView = (TextView) findViewById(R.id.guess_location_textview);
        mLeftDutyCycleTextView = (TextView) findViewById(R.id.left_duty_cycle_textview);
        mRightDutyCycleTextView = (TextView) findViewById(R.id.right_duty_cycle_textview);
        mMatchTimeTextView = (TextView) findViewById(R.id.match_time_textview);
        mGoOrMissionCompleteButton = (Button) findViewById(R.id.go_or_mission_complete_button);
        mJumboGoOrMissionCompleteButton = (Button) findViewById(R.id.jumbo_go_or_mission_complete_button);
        mJumbotronLinearLayout = findViewById(R.id.jumbo_linear_layout);

        mTargetGPSTextView = findViewById(R.id.target_GPS_value);
        mDistanceTextView = findViewById(R.id.distance_value);
        mJumboXTextView = findViewById(R.id.jumbo_x);
        mJumboYTextView = findViewById(R.id.jumbo_y);
       mMainLayout = findViewById(R.id.Main_Relative_Layount);


        // When you start using the real hardware you don't need test buttons.
        boolean hideFakeGpsButtons = false;
        if (hideFakeGpsButtons) {
            TableLayout fakeGpsButtonTable = (TableLayout) findViewById(R.id.fake_gps_button_table);
            fakeGpsButtonTable.setVisibility(View.GONE);
        }

        mScripts = new Scripts(this);
        setLocationToColor(1, BallColor.RED);
        setLocationToColor(2, BallColor.WHITE);
        setLocationToColor(3, BallColor.BLUE);
        setState(State.READY_FOR_MISSION);
    }

    public void setState(State newState) {
        if (mState == State.READY_FOR_MISSION && newState != State.INITIAL_STRAIGHT) {
            return;
        }
        mStateStartTime = System.currentTimeMillis();
        mCurrentStateTextView.setText(newState.name());
        speak(newState.name().replace("_", " ").toLowerCase());
        switch (newState) {
            case READY_FOR_MISSION:
                mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.green_button);
                mGoOrMissionCompleteButton.setText("Go!");
                mJumboGoOrMissionCompleteButton.setBackgroundResource(R.drawable.green_button);
                mJumboGoOrMissionCompleteButton.setText("Go!");
                sendWheelSpeed(0, 0);
                break;
            case INITIAL_STRAIGHT:
                sendWheelSpeed(90,LOWEST_DESIRABLE_DUTY_CYCLE);
                break;
            case DRIVE_TOWARDS_NEAR_BALL:
                //Nothing here. All in loop
                break;
            case IMAGE_REC_NEAR:
                ConeDetection();

                break;
            case NEAR_BALL_SCRIPT:
                sendWheelSpeed(0,0);
                mGpsInfoTextView.setText("---");
                mGuessXYTextView.setText("---");
                mScripts.nearBallScript();
                mViewFlipper.setDisplayedChild(2);
                break;
            case DRIVE_TOWARDS_FAR_BALL:
                // Nothing here. All the work happens in the loop function.
                break;
            case IMAGE_REC_FAR:
                break;
            case FAR_BALL_SCRIPT:
                mScripts.farBallScript();
                break;
            case DRIVE_TOWARDS_HOME:
                // Nothing here. All the work happens in the loop function.
                break;
            case IMAGE_REC_HOME:
                break;
            case WAITING_FOR_PICKUP:
                sendWheelSpeed(0,0);
                break;
            case SEEKING_HOME:
                // Nothing here. All the work happens in the loop function.
                break;
        }


        mState = newState;
    }

    /**
     * Use this helper method to set the color of a ball.
     * The location value here is 1 based.  Send 1, 2, or 3
     * Side effect: Updates the UI with the appropriate ball color resource image.
     */
    public void setLocationToColor(int location, BallColor ballColor) {
        mBallImageButtons[location - 1].setImageResource(BALL_DRAWABLE_RESOURCES[ballColor.ordinal()]);
        mLocationColors[location - 1] = ballColor;

        for(int i=1; i<4; i++){
            setBallPositions(i,mLocationColors[i-1]);

        }

    }

    public void setBallPositions(int location, BallColor ballColor){
        if(ballColor==BallColor.BLUE || ballColor==BallColor.YELLOW){
            if(mOnRedTeam){
                mFarBallLocation=location;
                Toast.makeText(this,"Far"+location,Toast.LENGTH_SHORT).show();
                if(ballColor==BallColor.BLUE){
                    mFarBallGpsY=50;
                }
                else{
                    mFarBallGpsY=-50;
                }
            }
            else{
                mNearBallLocation=location;
                Toast.makeText(this,"near"+location,Toast.LENGTH_SHORT).show();
                if(ballColor==BallColor.BLUE){
                    mNearBallGpsY=-50;
                }
                else{
                    mNearBallGpsY=50;
                }
            }
        }

        if(ballColor==BallColor.RED || ballColor==BallColor.GREEN){
            if(mOnRedTeam){
                mNearBallLocation=location;
                Toast.makeText(this,"near"+location,Toast.LENGTH_SHORT).show();
                if(ballColor==BallColor.RED){
                    mNearBallGpsY=-50;
                }
                else{
                    mNearBallGpsY=50;
                }
            }
            else{
                mFarBallLocation=location;
                Toast.makeText(this,"Far"+location,Toast.LENGTH_SHORT).show();
                if(ballColor==BallColor.RED){
                    mFarBallGpsY=50;
                }
                else{
                    mFarBallGpsY=-50;
                }
            }
        }

        if(ballColor==BallColor.BLACK || ballColor==BallColor.WHITE){
            if(ballColor==BallColor.WHITE){
                Toast.makeText(this,"White"+location,Toast.LENGTH_SHORT).show();
                mWhiteBallLocation=location;
            }
            else{
                mWhiteBallLocation=0;
                Toast.makeText(this,"Black"+location,Toast.LENGTH_SHORT).show();
            }
        }
    }



    /**
     * Used to get the state time in milliseconds.
     */
    private long getStateTimeMs() {
        return System.currentTimeMillis() - mStateStartTime;
    }

    /**
     * Used to get the match time in milliseconds.
     */
    private long getMatchTimeMs() {
        return System.currentTimeMillis() - mMatchStartTime;
    }


    // --------------------------- Methods added ---------------------------

    @Override
    public void loop() {
        super.loop();

        //REMOVE THIS as it is only for NEAR BALL DISTANCE DEBUGGING
        double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, NEAR_BALL_GPS_X,
                mNearBallGpsY);
        mDistanceTextView.setText(" "+distanceFromTarget);

        //Log.d(TAG, "This is loop within our subclass of Robot Activity");
        mStateTimeTextView.setText("" + getStateTimeMs() / 1000);
        mGuessXYTextView.setText("(" + (int) mGuessX + ", " + (int) mGuessY + ")");

//    mJumboXTextView.setText("" + (int)mCurrentGpsX);
//    mJumboYTextView.setText("" + (int)mCurrentGpsY);

        mJumboXTextView.setText("" + (int)mGuessX);
        mJumboYTextView.setText("" + (int)mGuessY);

        //DONE: look at iphone picture
        if (mConeFound) {
            mJumbotronLinearLayout.setBackgroundColor(Color.parseColor("#ff8000"));
        } else if(mCurrentGpsHeading != NO_HEADING) {
            mJumbotronLinearLayout.setBackgroundColor(Color.GREEN);
        } else{
            mJumbotronLinearLayout.setBackgroundColor(Color.LTGRAY);
        }

        long timeRemainingSeconds = MATCH_LENGTH_MS / 1000;
        if (mState != State.READY_FOR_MISSION) {
            timeRemainingSeconds = (MATCH_LENGTH_MS - getMatchTimeMs()) / 1000;
            if (getMatchTimeMs() > MATCH_LENGTH_MS) {
                setState(State.READY_FOR_MISSION);
            }
        }
        mMatchTimeTextView.setText(getString(R.string.time_format,
                timeRemainingSeconds / 60, timeRemainingSeconds % 60));

        if(mConeFound){
            if(mConeLeftRightLocation<0){
                Log.d(TAG,"Turn Left Some.");
            }
            if (mConeSize>0.1){
                Log.d(TAG,"Get Closer.");
            }
        }
        switch (mState) {
            case READY_FOR_MISSION:
                break;
            case INITIAL_STRAIGHT:
                if (getStateTimeMs() > 2500){
                    setState(State.DRIVE_TOWARDS_NEAR_BALL);
                }
                break;
            case DRIVE_TOWARDS_NEAR_BALL:
                seekTargetAt(NEAR_BALL_GPS_X, mNearBallGpsY);
                break;
            case IMAGE_REC_NEAR:
                ConeDetection();
                break;
            case NEAR_BALL_SCRIPT:
                sendWheelSpeed(0,0);
                break;
            case DRIVE_TOWARDS_FAR_BALL:
                sendWheelSpeed(0,0);
//                seekTargetAt(FAR_BALL_GPS_X, mFarBallGpsY);
                break;
            case IMAGE_REC_FAR:
                ConeDetection();
                sendWheelSpeed(0,0);
                break;
            case FAR_BALL_SCRIPT:
                sendWheelSpeed(0,0);
                break;
            case DRIVE_TOWARDS_HOME:
//                seekTargetAt(0,0);
                break;
            case IMAGE_REC_HOME:
                ConeDetection();
                break;
            case WAITING_FOR_PICKUP:
                if (getStateTimeMs() > 8000) {
                    setState(State.SEEKING_HOME);
                }
                break;
            case SEEKING_HOME:
                seekTargetAt(0, 0);
                if (getStateTimeMs() > 8000) {
                    setState(State.WAITING_FOR_PICKUP);
                }
                break;
        }
    }

    private void seekTargetAt(double xTarget, double yTarget) {

        Toast.makeText(this, "Seeking", Toast.LENGTH_SHORT).show();
        int leftDutyCycle = LEFT_PWM_VALUE_FOR_STRAIGHT;
        int rightDutyCycle = RIGHT_PWM_VALUE_FOR_STRAIGHT;
//        distance
        double targetHeading = NavUtils.getTargetHeading(mCurrentGpsX, mCurrentGpsY, xTarget, yTarget);
        double leftTurnAmount = NavUtils.getLeftTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
        double rightTurnAmount = NavUtils.getRightTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
        if (leftTurnAmount < rightTurnAmount) {
            leftDutyCycle = LEFT_PWM_VALUE_FOR_STRAIGHT - (int) (leftTurnAmount); // Using a VERY simple plan. :)
            leftDutyCycle = Math.max(leftDutyCycle, LOWEST_DESIRABLE_DUTY_CYCLE);
        } else {
            rightDutyCycle = RIGHT_PWM_VALUE_FOR_STRAIGHT - (int) (rightTurnAmount); // Could also scale it.
            rightDutyCycle = Math.max(rightDutyCycle, LOWEST_DESIRABLE_DUTY_CYCLE);
        }

        double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, xTarget,
                yTarget);
        mTargetGPSTextView.setText("( "+xTarget+", "+yTarget+")");
        mDistanceTextView.setText(" "+distanceFromTarget);
//        mCommand = "WHEEL SPEED FORWARD " + leftDutyCycle + " FORWARD " + rightDutyCycle;
//        mCommandTextView.setText(mCommand);

        if (mState == State.DRIVE_TOWARDS_NEAR_BALL) {
            if (distanceFromTarget < ACCEPTED_DISTANCE_AWAY_FT && mConeFound) {
                if(doImageRec){
                    setState(State.IMAGE_REC_NEAR);
                }
                else{
                    setState(State.NEAR_BALL_SCRIPT);
                }
            }
        }

        sendWheelSpeed((int) leftDutyCycle, (int) rightDutyCycle);

        //mTargetHeadingTextView.setText(" " + (int) (targetHeading));

    }


    // --------------------------- Drive command ---------------------------


    @Override
    public void sendWheelSpeed(int leftDutyCycle, int rightDutyCycle) {
        super.sendWheelSpeed(leftDutyCycle, rightDutyCycle);
        mLeftDutyCycleTextView.setText("Left\n" + mLeftDutyCycle);
        mRightDutyCycleTextView.setText("Right\n" + mRightDutyCycle);
    }


    // --------------------------- Sensor listeners ---------------------------

    @Override
    public void onLocationChanged(double x, double y, double heading, Location location) {
        super.onLocationChanged(x, y, heading, location);

        String gpsInfo = getString(R.string.xy_format, mCurrentGpsX, mCurrentGpsY);
        if (mCurrentGpsHeading != NO_HEADING) {
            gpsInfo += " " + getString(R.string.degrees_format, mCurrentGpsHeading);
        } else {
            gpsInfo += " ?°";
        }

        // TODO: Once image rec is done, move this area to the loop function!
//        if (mCurrentGpsHeading != NO_HEADING) {
//            mJumbotronLinearLayout.setBackgroundColor(Color.GREEN);
//        } else {
//            mJumbotronLinearLayout.setBackgroundColor(Color.LTGRAY);
//        }


        gpsInfo += "   " + mGpsCounter;
        mGpsInfoTextView.setText(gpsInfo);


        if(mState == State.IMAGE_REC_NEAR){
            double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, NEAR_BALL_GPS_X,
                    mNearBallGpsY);

        }

        if (mState == State.DRIVE_TOWARDS_FAR_BALL) {
            double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, FAR_BALL_GPS_X,
                    mFarBallGpsY);
            if (distanceFromTarget < ACCEPTED_DISTANCE_AWAY_FT) {
                if(doImageRec){
                    setState(State.IMAGE_REC_FAR);
                }
                else {
                    setState(State.FAR_BALL_SCRIPT);
                }
            }
        }

        if (mState == State.DRIVE_TOWARDS_HOME) {
            double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY, 0,0);
            if (distanceFromTarget < ACCEPTED_DISTANCE_AWAY_FT) {
                setState(State.WAITING_FOR_PICKUP);
            }
        }
    }

    @Override
    public void onSensorChanged(double fieldHeading, float[] orientationValues) {
        super.onSensorChanged(fieldHeading, orientationValues);
        mSensorOrientationTextView.setText(getString(R.string.degrees_format, mCurrentSensorHeading));
    }

    // --------------------------- Button Handlers ----------------------------

    /**
     * Helper method that is called by all three golf ball clicks.
     */
    private void handleBallClickForLocation(final int location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(GolfBallDeliveryActivity.this);
        builder.setTitle("What was the real color?").setItems(R.array.ball_colors,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        GolfBallDeliveryActivity.this.setLocationToColor(location, BallColor.values()[which]);
                    }
                });
        builder.create().show();
    }

    /**
     * Click to the far left image button (Location 1).
     */
    public void handleBallAtLocation1Click(View view) {
        handleBallClickForLocation(1);
    }

    /**
     * Click to the center image button (Location 2).
     */
    public void handleBallAtLocation2Click(View view) {
        handleBallClickForLocation(2);
    }

    /**
     * Click to the far right image button (Location 3).
     */
    public void handleBallAtLocation3Click(View view) {
        handleBallClickForLocation(3);
    }

    /**
     * Sets the mOnRedTeam boolean value as appropriate
     * Side effects: Clears the balls
     *
     * @param view
     */
    public void handleTeamChange(View view) {
        setLocationToColor(1, BallColor.NONE);
        setLocationToColor(2, BallColor.NONE);
        setLocationToColor(3, BallColor.NONE);
        if (mOnRedTeam) {
            mOnRedTeam = false;
            mTeamChangeButton.setBackgroundResource(R.drawable.blue_button);
            mTeamChangeButton.setText("Team Blue");
        } else {
            mOnRedTeam = true;
            mTeamChangeButton.setBackgroundResource(R.drawable.red_button);
            mTeamChangeButton.setText("Team Red");
        }
        Toast.makeText(this,""+mConeSize,Toast.LENGTH_SHORT).show();
        Ball_Script2();
        // setTeamToRed(mOnRedTeam); // This call is optional. It will reset your GPS and sensor heading values.
    }

    /**
     * Sends a message to Arduino to perform a ball color test.
     */
    public void handlePerformBallTest(View view) {
        // This is what we'd really do...
//    sendCommand("CUSTOM balltest");

        // But for testing we'll cheat
        sendCommand("getBallColors");
    }

    @Override
    protected void onCommandReceived(String receivedCommand) {
        super.onCommandReceived(receivedCommand);

         if (receivedCommand.equalsIgnoreCase("L-1")) {
            setLocationToColor(1, BallColor.NONE);
        }else if (receivedCommand.equalsIgnoreCase("L0")) {
             setLocationToColor(1, BallColor.BLACK);
         }else if (receivedCommand.equalsIgnoreCase("L1")) {
             setLocationToColor(1, BallColor.BLUE);
         }else if (receivedCommand.equalsIgnoreCase("L2")) {
             setLocationToColor(1, BallColor.GREEN);
         }else if (receivedCommand.equalsIgnoreCase("L3")) {
             setLocationToColor(1, BallColor.RED);
         }else if (receivedCommand.equalsIgnoreCase("L4")) {
             setLocationToColor(1, BallColor.YELLOW);
         }else if (receivedCommand.equalsIgnoreCase("L5")) {
             setLocationToColor(1, BallColor.WHITE);
         }
         else if (receivedCommand.equalsIgnoreCase("M-1")) {
             setLocationToColor(2, BallColor.NONE);
         }else if (receivedCommand.equalsIgnoreCase("M0")) {
             setLocationToColor(2, BallColor.BLACK);
         }else if (receivedCommand.equalsIgnoreCase("M1")) {
             setLocationToColor(2, BallColor.BLUE);
         }else if (receivedCommand.equalsIgnoreCase("M2")) {
             setLocationToColor(2, BallColor.GREEN);
         }else if (receivedCommand.equalsIgnoreCase("M3")) {
             setLocationToColor(2, BallColor.RED);
         }else if (receivedCommand.equalsIgnoreCase("M4")) {
             setLocationToColor(2, BallColor.YELLOW);
         }else if (receivedCommand.equalsIgnoreCase("M5")) {
             setLocationToColor(2, BallColor.WHITE);
         }
         else if (receivedCommand.equalsIgnoreCase("R-1")) {
             setLocationToColor(3, BallColor.NONE);
         }else if (receivedCommand.equalsIgnoreCase("R0")) {
             setLocationToColor(3, BallColor.BLACK);
         }else if (receivedCommand.equalsIgnoreCase("R1")) {
             setLocationToColor(3, BallColor.BLUE);
         }else if (receivedCommand.equalsIgnoreCase("R2")) {
             setLocationToColor(3, BallColor.GREEN);
         }else if (receivedCommand.equalsIgnoreCase("R3")) {
             setLocationToColor(3, BallColor.RED);
         }else if (receivedCommand.equalsIgnoreCase("R4")) {
             setLocationToColor(3, BallColor.YELLOW);
         }else if (receivedCommand.equalsIgnoreCase("R5")) {
             setLocationToColor(3, BallColor.WHITE);
         }




    }

    AlertDialog alert;

    /**
     * Clicks to the red arrow image button that should show a dialog window.
     */
    public void handleDrivingStraight(View view) {
        Toast.makeText(this, "handleDrivingStraight", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder builder = new AlertDialog.Builder(GolfBallDeliveryActivity.this);
        builder.setTitle("Driving Straight Calibration");
        View dialoglayout = getLayoutInflater().inflate(R.layout.driving_straight_dialog, (ViewGroup) getCurrentFocus());
        builder.setView(dialoglayout);
        final NumberPicker rightDutyCyclePicker = (NumberPicker) dialoglayout.findViewById(R.id.right_pwm_number_picker);
        rightDutyCyclePicker.setMaxValue(255);
        rightDutyCyclePicker.setMinValue(0);
        rightDutyCyclePicker.setValue(mRightStraightPwmValue);
        rightDutyCyclePicker.setWrapSelectorWheel(false);
        final NumberPicker leftDutyCyclePicker = (NumberPicker) dialoglayout.findViewById(R.id.left_pwm_number_picker);
        leftDutyCyclePicker.setMaxValue(255);
        leftDutyCyclePicker.setMinValue(0);
        leftDutyCyclePicker.setValue(mLeftStraightPwmValue);
        leftDutyCyclePicker.setWrapSelectorWheel(false);
        Button doneButton = (Button) dialoglayout.findViewById(R.id.done_button);
        doneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
                mRightStraightPwmValue = rightDutyCyclePicker.getValue();
                alert.dismiss();
            }
        });
        final Button testStraightButton = (Button) dialoglayout.findViewById(R.id.test_straight_button);
        testStraightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
                mRightStraightPwmValue = rightDutyCyclePicker.getValue();
                //Toast.makeText(GolfBallDeliveryActivity.this, "TODO: Implement the drive straight test", Toast.LENGTH_SHORT).show();
                mScripts.testStraightScript();
            }
        });
        alert = builder.create();
        alert.show();
    }

    /**
     * Test GPS point when going to the Far ball (assumes Blue Team heading to red ball).
     */
    public void handleFakeGpsF0(View view) {
        onLocationChanged(165, 50, NO_HEADING, null); // Midfield
    }

    public void handleFakeGpsF1(View view) {
        onLocationChanged(90, 50, 0, null);
    }

    public void handleFakeGpsF2(View view) {
        onLocationChanged(90, -50, 135, null);
    }

    public void handleFakeGpsF3(View view) {
        onLocationChanged(240, 41, 35, null); // Within range!
    }

    public void handleFakeGpsH0(View view) {
        onLocationChanged(165, 0, -179.9, null); // Midfield
    }

    public void handleFakeGpsH1(View view) {
        onLocationChanged(11, 0, 179.9, null); // Out of range
    }

    public void handleFakeGpsH2(View view) {
        onLocationChanged(9, 0, -170, null); // Within range!
    }

    public void handleFakeGpsH3(View view) {
        onLocationChanged(0, -9, -170, null); // Within range!
    }

    public void handleSetOrigin(View view) {
        mFieldGps.setCurrentLocationAsOrigin();
    }

    public void handleSetXAxis(View view) {
        mFieldGps.setCurrentLocationAsLocationOnXAxis();
    }

    public void handleZeroHeading(View view) {
        mFieldOrientation.setCurrentFieldHeading(0);
    }

    public void handleGoOrMissionComplete(View view) {
        Toast.makeText(this, ""+NEAR_BALL_GPS_X+" "+mNearBallGpsY+" "+mLocationColors[mNearBallLocation-1], Toast.LENGTH_SHORT).show();

        Toast.makeText(this, ""+FAR_BALL_GPS_X+" "+mFarBallGpsY+" "+mLocationColors[mFarBallLocation-1], Toast.LENGTH_SHORT).show();
        if (mState == State.READY_FOR_MISSION) {
            // This is the moment in time, when the match starts!
            mMatchStartTime = System.currentTimeMillis();
            updateMissionStrategyVariables();
            mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.red_button);
            mGoOrMissionCompleteButton.setText("Mission Complete!");
            mJumboGoOrMissionCompleteButton.setBackgroundResource(R.drawable.red_button);
            mJumboGoOrMissionCompleteButton.setText("Stop");
            setState(State.INITIAL_STRAIGHT);
        } else {
            setState(State.READY_FOR_MISSION);
        }
    }

    private void updateMissionStrategyVariables() {
//        // Goal is to set these values
//        mNearBallGpsY = -50;
//        mFarBallGpsY = 50;
//        mNearBallLocation = 3;
//        mWhiteBallLocation = 0;
//        mFarBallLocation = 1;
//
//        // Example of how you might write this code:
//        for (int i = 0; i < 3; i++) {
//            BallColor currentLocationColor = mLocationColors[i];
//            if (currentLocationColor == BallColor.WHITE) {
//                mWhiteBallLocation = i + 1;
//            }
//        }
//
//
//        if (mOnRedTeam) {
//            Log.d(TAG, "I'm on the red team!");
//        } else {
//            Log.d(TAG, "I'm on the blue team!");
//        }
//        Log.d(TAG, "Near ball location: " + mNearBallLocation + "  drop off at " + mNearBallGpsY);
//        Log.d(TAG, "Far ball location: " + mFarBallLocation + "  drop off at " + mFarBallGpsY);
//        Log.d(TAG, "White ball location: " + mWhiteBallLocation);
    }

    private void Ball_Script1 (){
        String attach="ATTACH 111111";
        sendCommand(attach);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 500);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -16, 108, -50,-125,0);
                sendCommand(command);
            }
        }, 1000);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -16, 108, -50,-180,0);
                sendCommand(command);
            }
        }, 1500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -16, 108, -50,-165,0);
                sendCommand(command);
            }
        }, 1750);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-180,0);
                sendCommand(command);
            }
        }, 2000);

        //Go Home without Hitting Shit
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.joint_angle_command,4,-125);
                sendCommand(command);
            }
        }, 2500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 3000);

    }

    private void Ball_Script2 (){
        String attach="ATTACH 111111";
        sendCommand(attach);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 500);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-180,0);
                sendCommand(command);
            }
        }, 1000);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-145,0);
                sendCommand(command);
            }
        }, 1500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-180,0);
                sendCommand(command);
            }
        }, 1750);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-145,0);
                sendCommand(command);
            }
        }, 2000);

        //Go Home without Hitting Shit
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.joint_angle_command,4,-125);
                sendCommand(command);
            }
        }, 2500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 3000);


    }

    private void Ball_Script3 (){

        String attach="ATTACH 111111";
        sendCommand(attach);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 500);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 23, 125, -79,-125,0);
                sendCommand(command);
            }
        }, 1000);

        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 23, 125, -79,-180,0);
                sendCommand(command);
            }
        }, 1500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 23, 125, -79,-145,0);
                sendCommand(command);
            }
        }, 1750);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, -3, 140, -90,-180,0);
                sendCommand(command);
            }
        }, 2000);

        //Go Home without Hitting Shit
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.joint_angle_command,4,-125);
                sendCommand(command);
            }
        }, 2500);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String command = getString(R.string.position_command, 0, 90, 0, -90, 90);
                sendCommand(command);
            }
        }, 3000);

    }

    private void ConeDetection(){

        if(mConeFound){
            if(mConeLeftRightLocation<0){ //Cone on Left, left goes slower right goes faster
//                double error_correctmConeLeftRightLocationion  = *RIGHT_PROPORTIONAL_CONTROL;
//                mRightDutyCycle = (int)(RIGHT_PWM_VALUE_FOR_TURN+error_correction);
//                mLeftDutyCycle = (int)(LEFT_PWM_VALUE_FOR_TURN);
                mMainLayout.setBackgroundColor(Color.RED);
                sendWheelSpeed(60,120);
            }
            else{ // Cone on Right
//                double error_correction = -mConeLeftRightLocation*LEFT_PROPORTIONAL_CONTROL;
//                mLeftDutyCycle = (int)(RIGHT_PWM_VALUE_FOR_STRAIGHT+error_correction);
//                mRightDutyCycle = (int)(RIGHT_PWM_VALUE_FOR_TURN);
                sendWheelSpeed(120,60);
                mMainLayout.setBackgroundColor(Color.BLUE);

            }
        }
        else{
//            if(CountNotFound )
            if(mState == State.IMAGE_REC_NEAR) {
                setState(State.DRIVE_TOWARDS_NEAR_BALL);
            }
            else if(mState == State.IMAGE_REC_FAR) {
                setState(State.DRIVE_TOWARDS_FAR_BALL);
            }
            else if(mState == State.IMAGE_REC_HOME) {
                setState(State.DRIVE_TOWARDS_HOME);
            }
        }
        if(mConeSize>MAX_SIZE_PERCENTAGE){
            if(mState == State.IMAGE_REC_NEAR) {
                mMainLayout.setBackgroundColor(Color.BLACK);
                sendWheelSpeed( 0,0);
                setState(State.NEAR_BALL_SCRIPT);
            }
            else if(mState == State.IMAGE_REC_FAR) {
                setState(State.FAR_BALL_SCRIPT);
            }
            else if(mState == State.IMAGE_REC_HOME) {
                setState(State.WAITING_FOR_PICKUP);
            }
        }
    }

    public void handleBall2(View view) {
        Ball_Script2();
    }
    public void handleBall3(View view) {
        Ball_Script3();
    }
    public void handleBall1(View view) {
        Ball_Script1();
    }


}
