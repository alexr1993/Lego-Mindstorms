
import lejos.nxt.*;
//import lejos.robotics.MirrorMotor;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.navigation.DifferentialPilot;
import lejos.robotics.subsumption.Arbitrator;
import lejos.robotics.subsumption.Behavior;
import java.lang.Runnable;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Timer;

/**
 * Demonstration of the Behavior subsumption classes.
 * 
 * Requires a wheeled vehicle with two independently controlled
 * motors connected to motor ports A and C, and 
 * a touch sensor connected to sensor  port 1 and
 * an ultrasonic sensor connected to port 3;
 * 
 * @author Brian Bagnall and Lawrie Griffiths, modified by Roger Glassey
 *
 */
public class SubsumptionArch {
    private DifferentialPilot pilot;
    private Arbitrator arbitrator;
    private RegulatedMotor leftMotor = Motor.A;
    private RegulatedMotor rightMotor = Motor.C;
    private LightSensor l = new LightSensor(SensorPort.S4);
    private int lightThreshold = 400; // With floodlight on, higher than this reading implies we're facing a wall

	// For figuring out turning
	private double pi = Math.PI;
	private int radius = 10;
	private int circleLength = (int)(2 * radius * pi);

    public static void main(String[] args) {
        SubsumptionArch s = new SubsumptionArch();
        UltrasonicSensor sonar = new UltrasonicSensor(SensorPort.S3);
        sonar.continuous();

        s.start();
    }

    public SubsumptionArch() {
        pilot = new DifferentialPilot(2.1f, 4.4f, leftMotor, rightMotor);
		pilot.setTravelSpeed(circleLength/10); // cm/s - speed of circleLength/4 per second means 1 second to turn a corner

        Behavior b1 = new DriveForward();
        Behavior b2 = new FollowCorner();
        Behavior b3 = new CorrectPath();
        Behavior[] behaviorList = {b1, b2, b3};
        arbitrator = new Arbitrator(behaviorList);
		l.setFloodlight(ColorSensor.Color.WHITE);
        LCD.drawString("Press to Start", 0, 1);
        Button.waitForAnyPress();
    }

    public void start() {
        arbitrator.start();
    }

    class CorrectPath implements Behavior {
        private TouchSensor leftWhisker;
        private TouchSensor rightWhisker;
        private boolean LEFT_SIDE = false;
        private boolean RIGHT_SIDE = false;
		private boolean LIGHT_SENSOR = false;
        private boolean locked = false; // prevent both bumpers triggering at once
        private int turn_angle = 35;
        private int left_angle = turn_angle;
        private int right_angle = -turn_angle;
		private int light_sensor_angle = -90; // move from wall in front to wall on left
        private int distance = -2; // Negative distance is reverse, measured in cm

        public CorrectPath() {
            leftWhisker = new TouchSensor(SensorPort.S1);
            rightWhisker = new TouchSensor(SensorPort.S2);
        }

        public boolean takeControl() {
            if (locked) return false;
            LEFT_SIDE = leftWhisker.isPressed();
            RIGHT_SIDE = rightWhisker.isPressed();
			LIGHT_SENSOR = l.readNormalizedValue() > lightThreshold;
            return LEFT_SIDE || RIGHT_SIDE || LIGHT_SENSOR;
        }

        public void suppress() {
            //Since  this is highest priority behavior, suppress will never be called.
        }

        public void action() {
            locked = true; // this may not be necessary
            LCD.clear();
            LCD.drawString("CorrectPath", 0, 1);
            pilot.travel(distance);

            if (LEFT_SIDE) {
                // Turn right a bit
                pilot.rotate(right_angle);
            } else if (RIGHT_SIDE) {
				// Turn left a bit
				//pilot.rotate(left_angle);
                //Back up and turn around
                pilot.rotate(-90);
			} else if (LIGHT_SENSOR) {
				// Turn from facing wall head-on
				pilot.rotate(light_sensor_angle);
            } else {
                /* Light sensor has triggered, assume we are at corner */
                pilot.travel(distance);
                pilot.rotate(right_angle*2);
            }
            locked = false;
        }

        protected void finalize() {
        }
    }


    class FollowCorner implements Behavior {
        private UltrasonicSensor sonar;

        private int changeThreshold = 30;
        private int turnAngle = 100; // 100 is 100 degrees anticlockwise
        private Thread t;
        private boolean shouldTurn = false;
        private int maxDistances = 8;
		private int distsToCheck = 4;
        private Object lock = new Object();
        private int tooFar = 40;
		private ArrayList<Integer> distances = new ArrayList<>();
		private int mean;
		private int prevDist;
		private boolean suppressed = false;

		private int turn = 0; // Severity of turn required
        public FollowCorner() {
            sonar = new UltrasonicSensor(SensorPort.S3);
            t = new Thread(new Runnable() {
				/* Infer from the distances list how much we need to turn */
                private void setTurn() {
					if (distances.size() != maxDistances) return;
					// Average the last x distances
					prevDist = 0;
					for (int i = distsToCheck; i < maxDistances; i++) {
						prevDist += distances.get(i);
					}
					prevDist /= (maxDistances-distsToCheck);

					// Find average change since the earliest dist in list
					int total = 0;
                    for(int i = 0; i < distsToCheck; i++){
                        total += distances.get(i) - prevDist;
                    }
					mean = total / distsToCheck;
					if (mean > 100) turn = 90;
					else if ( (mean > 5) || (distances.get(1) > 25)) turn = 40;
					else turn = 0;
                }
                public void run() {
					/* Loop: Check distance, notify control methods to turn */
                    while(true) {
						sonar.ping();
                        int distance = sonar.getDistance();
                        if (distances.size() == maxDistances) {
                            distances.remove(maxDistances-1); // remove last element
                        }
                        distances.add(0, distance);
						synchronized (lock) {
							setTurn();
                        }
						LCD.clearDisplay();
                        LCD.drawString("Curr: " + distance, 0, 1);
                        LCD.drawString("Turn: " + turn, 0, 2);
                        LCD.drawString(distances.toString(), 0, 3);
						LCD.drawString("PrevDist: " + prevDist, 0, 4);
						LCD.drawString("Mean: " + mean, 0, 5);

                        try {
                            Thread.sleep(250); // Wait between readings
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            t.start();
        }

        public boolean takeControl() {
            return turn > 0;
        }

        public void suppress() {
			suppressed = true;
        }

        public void action() {
            LCD.clearDisplay();
            LCD.drawString("Follow Corner", 0, 1);

			int sleepTime = 2000/90 * turn; // 45 degrees is 1 second, 90 is 2
            sleepTime += 400;
            pilot.arcForward(radius);

			long startTime = System.currentTimeMillis();
			while (System.currentTimeMillis() - sleepTime < startTime && ! suppressed) {}

			synchronized (lock) {
                shouldTurn = false;
            }
			suppressed = false;
        }

        protected void finalize() {
        }
    }

    class DriveForward implements Behavior {
        private boolean _suppressed = false;

        public boolean takeControl() {
            return true;  // this behavior always wants control.
        }

        public void suppress() {
            _suppressed = true;// standard practice for suppress methods
        }

        public void action() {
            LCD.clearDisplay();
            LCD.drawString("DriveForward", 0, 1);
            _suppressed = false;
            pilot.forward();
            while (!_suppressed) {
                //LCD.clearDisplay();
                //LCD.drawString("Light: " + String.valueOf(l.readValue()) + ", " + String.valueOf(l.readNormalizedValue()), 0, 1);

                Thread.yield(); //don't exit till suppressed
            }
            pilot.stop();
        }

        protected void finalize() {
        }
    }
}

/*
class AvoidHeadOn implements Behavior {

	private boolean _suppressed = false;
	
	private LightSensor light;
	
	public AvoidHeadOn() {
		light =  new LightSensor(SensorPort.S1);
	}
	
	public boolean takeControl() {
		return light.readValue() <= 40;
	}
	
	public void suppress(){
		_suppressed = true;
	}
	
	public void action() {
		main.SubsumptionArch.leftMotor.stop();
		main.SubsumptionArch.rightMotor.stop();
	}
	
}
*/

/*
class DetectWall implements Behavior
{

  public DetectWall()
  {
    touch = new TouchSensor(SensorPort.S1);
    sonar = new UltrasonicSensor(SensorPort.S3);
  }

  public boolean takeControl()
  {
    sonar.ping();
    
    return touch.isPressed() || sonar.getDistance() < 25;
  }

  public void suppress()
  {
    //Since  this is highest priority behavior, suppress will never be called.
  }

  public void action()
  {
    main.SubsumptionArch.leftMotor.rotate(-180, true);// start Motor.A rotating backward
    main.SubsumptionArch.rightMotor.rotate(-360);  // rotate C farther to make the turn
  }
  
  private TouchSensor touch;
  private UltrasonicSensor sonar;
}
*/

