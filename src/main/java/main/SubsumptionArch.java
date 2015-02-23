
import lejos.nxt.*;
//import lejos.robotics.MirrorMotor;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.navigation.DifferentialPilot;
import lejos.robotics.subsumption.Arbitrator;
import lejos.robotics.subsumption.Behavior;

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

    public static void main(String[] args) {
        SubsumptionArch s = new SubsumptionArch();
        UltrasonicSensor sonar = new UltrasonicSensor(SensorPort.S3);
        sonar.continuous();

        s.start();
    }

    public SubsumptionArch() {
        pilot = new DifferentialPilot(2.1f, 4.4f, leftMotor, rightMotor);
        pilot.setTravelSpeed(5); // cm/s

        l.setFloodlight(true);
        Behavior b1 = new DriveForward();
        Behavior b2 = new FollowCorner();
        Behavior b3 = new CorrectPath();
        Behavior[] behaviorList = {b1, b2, b3};
        arbitrator = new Arbitrator(behaviorList);

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
        private boolean locked = false; // prevent both bumpers triggering at once
        private int turn_angle = 25;
        private int left_angle = turn_angle;
        private int right_angle = -turn_angle;
        private int distance = -2; // Negative distance is reverse, measured in cm

        public CorrectPath() {
            leftWhisker = new TouchSensor(SensorPort.S1);
            rightWhisker = new TouchSensor(SensorPort.S2);
        }

        public boolean takeControl() {
            if (locked) return false;
            LEFT_SIDE = leftWhisker.isPressed();
            RIGHT_SIDE = rightWhisker.isPressed();

            return LEFT_SIDE || RIGHT_SIDE || l.readNormalizedValue() > lightThreshold;
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
                pilot.rotate(left_angle);
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
        private boolean _suppressed = false;
        private float prev_dist = 255; // if this changes by more than the threshold we've lost the wall
        private float changeThreshold = 10;
        private int turnAngle = 100; // 100 is 100 degrees anticlockwise

        public FollowCorner() {
            sonar = new UltrasonicSensor(SensorPort.S3);
            sonar.continuous();
        }

        public boolean takeControl() {
            try {
                // Wait for robot to move since last reading
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            float distance = sonar.getRange();
            float change = distance - prev_dist;
            LCD.clearDisplay();
            LCD.drawString("Prev: " + prev_dist, 0, 1);
            LCD.drawString("Curr: " + distance, 0, 2);
            LCD.drawString("Change: " + change, 0, 3);
            // Only take control when distance increases dramatically
            boolean control = change > changeThreshold;
            LCD.drawString("TakeControl?: " + control, 0, 4);
            this.prev_dist = distance;
            return change > 0;
        }

        public void suppress() {
            _suppressed = true;// standard practice for suppress methods
        }

        public void action() {
            _suppressed = false;
            LCD.clearDisplay();
            LCD.drawString("Follow Corner", 0, 1);
            pilot.rotate(turnAngle);
            try {
                Thread.sleep(1000); // wait so we can see the state in the LCD display
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
                //LCD.scroll();
                //LCD.drawString("Light: " + String.valueOf(l.readValue()) + ", " + String.valueOf(l.readNormalizedValue()), 0, 1);
                //LCD.drawString("Distance: " + String.valueOf(s.getDistance()), 0, 1);
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

