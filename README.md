# Lego-Mindstorms
Intelligent Control and Cognitive Systems coursework

This was hacked together. It is mine and vendorofdoom's creation, an adaptation of some samples obtained from lejos.org - the providers of this java implementation on the Lego Mindstorms kit.

* * *
The robot implemented in this project will follow walls when configured with a left facing ultrasound, and frontal bumpers +  a light sensor.

09/02:
- Figure out what an intelligent robot should be able to do
- What kind of data SONAR and the light sensor provide, which will help the robot most

20/02:
- Need to move ultrasonic sensor further forwards as it is currently aligned with the 
  hweels making corner turning difficult. May need add some weight in the back of bot 
  also as it appears to be a little off balance. 

Robot functionality checklist:

Robot should be able to 
- Wander around when no wall is in sight
	- Moving in a spiral might be a good way to 'blindly' find a wall
- Identify when close to a wall and align itself parallel to it
- Travel alongside wall
- Identify a corner which it is on the outside of and follow the wall around this corner - this is difficult
- Identify a corner which it is on the inside of and turn to keep following the wall - use light sensor for this
- Identify objects which cannot be followed but pose a risk of getting stuck and avoid them like the plague
