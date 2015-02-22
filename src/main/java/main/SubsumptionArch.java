
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
        s.start();
    }

    public SubsumptionArch() {
        pilot = new DifferentialPilot(2.1f, 4.4f, leftMotor, rightMotor);
        pilot.setTravelSpeed(5); // cm/s

        l.setFloodlight(true);
        Behavior b1 = new DriveForward();
        Behavior b2 = new FollowWall();
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
        private int turn_angle = 15;
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
            LCD.scroll();
            LCD.drawString("CorrectPath", 0, 1);
            pilot.travel(distance);

            if (LEFT_SIDE) {
                // Turn right a bit
                pilot.rotate(right_angle);

            } else {
                // Turn left a bit
                pilot.rotate(left_angle);
            }
            locked = false;
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
            LCD.scroll();
            LCD.drawString("DriveForward", 0, 1);
            _suppressed = false;
            pilot.forward();
            while (!_suppressed) {
                LCD.drawString("Light: " + String.valueOf(l.readValue()) + ", " + String.valueOf(l.readNormalizedValue()), 0, 1);
                Thread.yield(); //don't exit till suppressed
            }
            pilot.stop();
        }

        protected void finalize() {
        }
    }

    class FollowWall implements Behavior {

        private UltrasonicSensor sonar;
        private boolean _suppressed = false;
        private boolean TOO_FAR = false;
        private int wall_dist = 20; // how far to keep to/from wall

        public FollowWall() {
            sonar = new UltrasonicSensor(SensorPort.S3);
        }

        public boolean takeControl() {
            sonar.ping();
            TOO_FAR = (sonar.getDistance() > wall_dist);
            return sonar.getDistance() > wall_dist;
        }

        public void suppress() {
            _suppressed = true;// standard practice for suppress methods
        }

        public void action() {
            LCD.scroll();
            LCD.drawString("units: " + sonar.getUnits(), 0, 1);
            _suppressed = false;
             if (TOO_FAR) { // too far from wall, probably turning a corner
                int count = 0; // if turned loads stop and try something else
                int max = 30;
                while(!_suppressed) {
                    if(sonar.getDistance() > wall_dist && count < max) {
                        LCD.drawString("Distance: " + String.valueOf(sonar.getDistance()), 0, 2);
                        pilot.steer(-90);
                        sonar.ping();
                        count++;
                    }
                    else {
                        pilot.forward();
                    }
                }
            }
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

