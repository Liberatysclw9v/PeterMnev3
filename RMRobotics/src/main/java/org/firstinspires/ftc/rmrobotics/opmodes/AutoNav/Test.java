/**
 * Created by Peter on 2/11/2017.
 */

package org.firstinspires.ftc.rmrobotics.opmodes.AutoNav;

import android.view.ViewDebug;
import android.widget.ImageView;

import com.kauailabs.navx.ftc.AHRS;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DeviceInterfaceModule;

import com.qualcomm.robotcore.hardware.I2cAddr;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchImpl;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;
import com.vuforia.HINT;
import com.vuforia.PIXEL_FORMAT;
import com.vuforia.Vuforia;

import org.firstinspires.ftc.robotcontroller.internal.FtcRobotControllerActivity;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackable;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;

import org.opencv.core.Mat;



/**
 * Created by Peter on 12/15/2016.
 */

@Autonomous(name = "Test", group = "AutoNav")
public class Test extends LinearOpMode {

    //runtime calculations
    static ElapsedTime runtime = new ElapsedTime();

    //motors
    private DcMotor frontLeft;
    private DcMotor frontRight;
    private DcMotor backLeft;
    private DcMotor backRight;
    private DcMotor shootL;
    private DcMotor shootR;

    private RMVuforia vuforia;

    private Drive drive = null;

    //servo
    CRServo leftPusher;
    Servo rightPusher;
    Servo latch;

    //navx
    private final int NAVX_DIM_I2C_PORT = 0;
    private AHRS navx_device;
    private final byte NAVX_DEVICE_UPDATE_RATE_HZ = 50;
    I2cDeviceSynch rangeSensor = null;

    private ImageView mImageView;

    private double dir = 1;

    private final int RECOGNITION_DIST_Y = 330; // Distance to turn on OpenCV
    private final int RECOGNITION_TOLER_Y = 40; // Positioning tolerance along Y
    private final int RECOGNITION_TOLER_X = 30; // Positioning tolerance along Y
    private final int REPOSITION_DIST = 480; // Distance to back up if out of position to recognize button

    @Override
    public void runOpMode() throws InterruptedException {
        // Read config file
        AutoNavConfig cfg = new AutoNavConfig();
        cfg.ReadConfig(((FtcRobotControllerActivity) hardwareMap.appContext));
        if (cfg.isRight) dir = 1;
        else dir = -1;

        //init vuforia
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters(/*com.qualcomm.ftcrobotcontroller.R.id.cameraMonitorViewId*/);
        parameters.vuforiaLicenseKey = "AfMO3Zb/////AAAAGRP6yrVXWkBalSEp+NZax44NTzFOkclxZo99uF0DhcZXRp0O2qHl6wcPx8Bp+3dppnmePU1HwDeEwSmWl5k/QEHiTAXMEfc/DBjkAVcMvEBnJGaGEHjKsD4/YaU+cdVcU7Q+NbG7fT1KYcpRUu2btnjhHWCPPhS0mu2AtWNw87FuXX0ob8GCP9jc7fKqVkdgChkshd9aaqJ6113IosFisru2Jk2V098iWv20c3ASuEp0oZfFp6DtLKjx0GQWc7+PhM8rxNRRFNbvwTKAUeiQBRyDoh+UroSO39fon2BcFrStL6pEKa6pqIahZGaeIZXcbx9KpmfO6LNX2vkIgdbw+KZjcszjI0x1RYZDcCu4U5V6";
        parameters.cameraDirection = VuforiaLocalizer.CameraDirection.FRONT;
        parameters.cameraMonitorFeedback = VuforiaLocalizer.Parameters.CameraMonitorFeedback.AXES;
        parameters.useExtendedTracking = false;

        vuforia = new RMVuforia(parameters);
        Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 4);
        VuforiaTrackables targets = this.vuforia.loadTrackablesFromAsset("FTC_2016-17");


        // Enable frame grabbing
        Vuforia.setFrameFormat(PIXEL_FORMAT.RGB888, true); //This line is very important, make sure the keep the format constant throughout the program. I'm using the MotoG2. I've also tested on the ZTE speeds and I found that they use RGB888
        vuforia.setFrameQueueCapacity(1); //tells VuforiaLocalizer to only store one frame at a time

        mImageView = (ImageView) ((FtcRobotControllerActivity) hardwareMap.appContext).findViewById(com.qualcomm.ftcrobotcontroller.R.id.imageViewId);

        //gets trackable objects and puts them in an array
        VuforiaTrackable wheels = targets.get(0);
        wheels.setName("Wheels");
        VuforiaTrackable tools = targets.get(1);
        tools.setName("Tools");
        VuforiaTrackable legos = targets.get(2);
        legos.setName("Legos");
        VuforiaTrackable gears = targets.get(3);
        gears.setName("Gears");

        VuforiaTrackable firstTarget = null;
        VuforiaTrackable secondTarget = null;

        //Sets two targets to be the ones that will be tracked based on config.
        for (int it = 0; it < 4; it++) {
            if (targets.get(it).getName().equals(cfg.firstBeacon))
                firstTarget = targets.get(it);
            if (targets.get(it).getName().equals(cfg.secondBeacon))
                secondTarget = targets.get(it);
        }
        targets.activate();


        //init motors
        frontLeft = hardwareMap.dcMotor.get("wheelFL");
        frontRight = hardwareMap.dcMotor.get("wheelFR");
        backLeft = hardwareMap.dcMotor.get("wheelBL");
        backRight = hardwareMap.dcMotor.get("wheelBR");
        shootL = hardwareMap.dcMotor.get("shootL");
        shootR = hardwareMap.dcMotor.get("shootR");
        shootL.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        shootR.setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        //and servo
        leftPusher = hardwareMap.crservo.get("leftP");
        leftPusher.setPower(0);
        rightPusher = hardwareMap.servo.get("rightP");
        rightPusher.setPosition(0);

        latch = hardwareMap.servo.get("latch");
        latch.setPosition(0.5);

        // Create NAVX device
        DeviceInterfaceModule dim = hardwareMap.deviceInterfaceModule.get("dim");
        ADBLog(dim.toString());

        navx_device = AHRS.getInstance(hardwareMap.deviceInterfaceModule.get("dim"),
                NAVX_DIM_I2C_PORT,
                AHRS.DeviceDataType.kProcessedData,
                NAVX_DEVICE_UPDATE_RATE_HZ);

        ADBLog("NavX is ready");

        // Create optical sensor
        rangeSensor = new I2cDeviceSynchImpl(
                hardwareMap.i2cDevice.get("range"),
                I2cAddr.create8bit(0x62), // Sensor I2C address.
                false);
        rangeSensor.engage();
        //drive class for big roboto
        drive = new Drive(
                frontLeft,
                frontRight,
                backLeft,
                backRight,
                navx_device,
                telemetry,
                this
        );
        drive.ReverseDirection();

        targets.activate();
        float x = 0;
        float y = 0;
        Mat img = null;

        sleep(500);
        ADBLog("Initialization complete");
        telemetry.clear();
        telemetry.addData("Finished", "the initialization: " + ((cfg.isRed) ? "Red" : "Blue") + ", " + ((cfg.isRight) ? "Right" : "Left"));
        telemetry.addData("Targets", cfg.firstBeacon + ",  " + cfg.secondBeacon);
        telemetry.update();
        waitForStart();
/////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////
// Start here
        // Start the engine!!!
        // 1 encoder tick ~ 1 mm
        ADBLog("started the programmmm!!!!");
        try {
            ADBLogReset();
            ADBLog("Start");
//            drive.Stop();
//            sleep(100);
//            ADBLog(Integer.toString(drive.testBackLeft(1)));
//            sleep(700);
//            ADBLog(Integer.toString(drive.testBackLeft(0)));
//            sleep(100);
//            ADBLog(Integer.toString(drive.testBackRight(1)));
//            sleep(700);
//            ADBLog(Integer.toString(drive.testBackRight(0)));
//            sleep(100);
//            ADBLog(Integer.toString(drive.testFrontLeft(1)));
//            sleep(700);
//            ADBLog(Integer.toString(drive.testFrontLeft(0)));
//            sleep(100);
//            ADBLog(Integer.toString(drive.testFrontRight(1)));
//            sleep(700);
//            ADBLog(Integer.toString(drive.testFrontRight(0)));
//            sleep(100);


            ADBLog("Start test");

            drive.DriveByEncoders(0,1,200);
            ADBLog("rotate 90 degrees");
            drive.DriveByEncoders(45,1,500);
            drive.DriveByEncoders(0,1,800);
            drive.brake();
            drive.TurnToAngle(180);
            sleep(1000);
            drive.DriveByEncoders(180,1,300);
            drive.brake();


        } finally {
            ADBLog("Exiting opmode");
            drive.Stop();
            leftPusher.setPower(0);
            leftPusher.close();
            rightPusher.setPosition(0.1);
            rightPusher.close();
            latch.setPosition(0.5);
            latch.close();
            navx_device.close();
            stop();
        }
    }


    private volatile static double initial_time = 0;

    static synchronized public void ADBLogReset() {
        initial_time = runtime.milliseconds();
        RobotLog.d("Initial time: " + initial_time);
    }

    static synchronized public void ADBLog(String msg) {
        double t = -initial_time;
        t += runtime.milliseconds();
        RobotLog.d(String.format("%.0f", t) + ": " + msg);
    }

    // Load OpenCV libraries
    static {
        System.loadLibrary("opencv_java3");
    }
}


