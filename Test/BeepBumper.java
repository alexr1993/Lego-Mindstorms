import lejos.robotics.objectdetection.Feature;
import lejos.robotics.objectdetection.FeatureDetector;
import lejos.robotics.objectdetection.FeatureListener;
import lejos.robotics.objectdetection.TouchFeatureDetector;
import lejos.nxt.Motor;
import lejos.nxt.Button;
import lejos.nxt.SensorPort;
import lejos.nxt.Sound;
import lejos.nxt.TouchSensor;

public class BeepBumper implements FeatureListener{

	public static int LEFT_SIDE = 1;
	public static int RIGHT_SIDE = -1;

	public BeepBumper(final SensorPort leftTouch, final SensorPort rightTouch) {
		TouchSensor leftBump = new TouchSensor(leftTouch);
        TouchSensor rightBump = new TouchSensor(rightTouch);
		
		// Create object detection for each bumper and add BumpNavigator as a listener:
        FeatureDetector fd1 = new TouchFeatureDetector(leftBump, -4.5, 10);
        fd1.addListener(this);
        FeatureDetector fd2 = new TouchFeatureDetector(rightBump, 4.5, 10);
        fd2.addListener(this);
	}
	
	public static void main(String[] args) throws Exception {
		BeepBumper robot = new BeepBumper(SensorPort.S2, SensorPort.S3);
		Motor.A.forward();
		Motor.B.forward();
		while(!Button.ESCAPE.isDown()) {
			
		}
	}
	

	
	/**
     * causes the robot to back up, turn away from the obstacle.
     * returns when obstacle is cleared or if an obstacle is detected while traveling.
     */
	public void featureDetected(Feature feature, FeatureDetector detector) {
		detector.enableDetection(false);
		Sound.beepSequence();
		int side = 0;
		
		Motor.A.backward();
		Motor.B.backward();
		try {
			Thread.sleep(1000);
		
			// Identify which bumper was pressed:
			if(feature.getRangeReading().getAngle() < 0) {
				side = LEFT_SIDE;
				System.out.println("Left bumper hit!");
				Motor.B.forward();
				Motor.A.backward();
				Thread.sleep(500);
				Motor.A.forward();
				
			}	
			else {
				side = RIGHT_SIDE;
				System.out.println("Right bumper hit!");
				Motor.A.forward();
				Motor.B.backward();
				Thread.sleep(500);
				Motor.B.forward();
			}	
			
		}
		catch(Exception e) {
			
		}
			
		System.out.println("HALLO!");
		detector.enableDetection(true);
	}

}