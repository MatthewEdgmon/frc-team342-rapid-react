// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.MecanumDriveOdometry;
import edu.wpi.first.math.kinematics.MecanumDriveWheelSpeeds;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.util.sendable.SendableBuilder;
import edu.wpi.first.wpilibj.ADIS16470_IMU;
import edu.wpi.first.wpilibj.drive.MecanumDrive;
import edu.wpi.first.wpilibj2.command.MecanumControllerCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

// Static imports mean that variable names can be accessed without referencing the class name they came from
import static frc.robot.Constants.DriveConstants.*;

public class DriveSystem extends SubsystemBase {

  private CANSparkMax frontLeft;
  private CANSparkMax backLeft;
  private CANSparkMax frontRight;
  private CANSparkMax backRight;

  private SparkMaxPIDController frontLeftController;
  private SparkMaxPIDController backLeftController;
  private SparkMaxPIDController frontRightController;
  private SparkMaxPIDController backRightController;

  private RelativeEncoder frontLeftEncoder;
  private RelativeEncoder backLeftEncoder;
  private RelativeEncoder frontRightEncoder;
  private RelativeEncoder backRightEncoder;

  private boolean fieldOriented = true;
  private ADIS16470_IMU gyro;

  private MecanumDrive mecanumDrive;

  private MecanumDriveOdometry odometry;
  private TrajectoryConfig trajectoryConfig;

  private ProfiledPIDController rotationController;

  private double speedMultiplier = 0.8;

  /** Creates a new DriveSystem. */
  public DriveSystem() {
    // Capitalized and underscored variable names are statically imported constants from Constants.java
    frontLeft = new CANSparkMax(FRONT_LEFT_MOTOR, MotorType.kBrushless);
    backLeft = new CANSparkMax(BACK_LEFT_MOTOR, MotorType.kBrushless);
    frontRight = new CANSparkMax(FRONT_RIGHT_MOTOR, MotorType.kBrushless);
    backRight = new CANSparkMax(BACK_RIGHT_MOTOR, MotorType.kBrushless);

    // Current limits on breakers are set to 40 Amps
    frontLeft.setSmartCurrentLimit(CURRENT_LIMIT);
    backLeft.setSmartCurrentLimit(CURRENT_LIMIT);
    frontRight.setSmartCurrentLimit(CURRENT_LIMIT);
    backRight.setSmartCurrentLimit(CURRENT_LIMIT);

    // Voltage compensation in volts
    frontLeft.enableVoltageCompensation(NOMINAL_VOLTAGE);
    backLeft.enableVoltageCompensation(NOMINAL_VOLTAGE);
    frontRight.enableVoltageCompensation(NOMINAL_VOLTAGE);
    backRight.enableVoltageCompensation(NOMINAL_VOLTAGE);

    // Time in seconds to reach max velocity in open loop
    frontLeft.setOpenLoopRampRate(RAMP_RATE);
    backLeft.setOpenLoopRampRate(RAMP_RATE);
    frontRight.setOpenLoopRampRate(RAMP_RATE);
    backRight.setOpenLoopRampRate(RAMP_RATE);

    // PID Controllers
    frontLeftController = frontLeft.getPIDController();
    backLeftController = backLeft.getPIDController();
    frontRightController = frontRight.getPIDController();
    backRightController = backRight.getPIDController();

    // Encoders
    frontLeftEncoder = frontLeft.getEncoder();
    backLeftEncoder = backLeft.getEncoder();
    frontRightEncoder = frontRight.getEncoder();
    backRightEncoder = backRight.getEncoder();

    mecanumDrive = new MecanumDrive(frontLeft, backLeft, frontRight, backRight);
    gyro = new ADIS16470_IMU();

    odometry = new MecanumDriveOdometry(KINEMATICS, new Rotation2d(gyro.getAngle()));
    trajectoryConfig = new TrajectoryConfig(MAX_SPEED, MAX_ACCELERATION);

    rotationController = new ProfiledPIDController(0, 0, 0, new TrapezoidProfile.Constraints(MAX_ROTATION_SPEED, MAX_ROTATION_ACCELERATION));
  }

  /**
   * Drives based on whether driving is field oriented or not
   * 
   * @param xVelocity velocity of the robot moving forward
   * @param yVelocity velocity of the robot moving side-to-side 
   * @param rotationVelocity velocity of robot moving clockwise 
   **/
  public void drive(double xVelocity, double yVelocity, double rotationVelocity) {
    // Used for slow mode 
    double x = xVelocity * speedMultiplier;
    double y = yVelocity * speedMultiplier;
    double rotation = rotationVelocity * speedMultiplier;

    if (fieldOriented) {
      mecanumDrive.driveCartesian(y, x, rotation, -gyro.getAngle());
    } else {
      mecanumDrive.driveCartesian(y, x, rotation);
    }
  }

  public Pose2d getPose() {
    return odometry.getPoseMeters();
  }

  /**
   * Get the current speeds of the wheel as a MecanumDriveWheelSpeeds object. <br/>
   * Units are RPM.
   * 
   * @return the current wheel speeds
   */
  private MecanumDriveWheelSpeeds getWheelSpeeds() {
    return new MecanumDriveWheelSpeeds(
      frontLeftEncoder.getVelocity(),
      backLeftEncoder.getVelocity(),
      frontRightEncoder.getVelocity(),
      backRightEncoder.getVelocity()
    );
  }

  /**
   * Drive the wheel motors at specific velocities, using PID on each motor.
   * 
   * @param speeds the speeds at which to drive the wheels
   */
  private void drive(MecanumDriveWheelSpeeds speeds) {
    frontLeftController.setReference(speeds.frontLeftMetersPerSecond, ControlType.kVelocity);
    backLeftController.setReference(speeds.rearLeftMetersPerSecond, ControlType.kVelocity);
    frontRightController.setReference(speeds.frontRightMetersPerSecond, ControlType.kVelocity);
    backRightController.setReference(speeds.rearRightMetersPerSecond, ControlType.kVelocity);
  }

  /**
   * Get the default configuration for trajectory following commands, which includes the max velocity and max acceleration. <br/>
   * Can be changed to move backwards with the {@link edu.wpi.first.math.trajectory.TrajectoryConfig#setReversed(boolean) setReversed(boolean)} method.
   * 
   * @return the trajectory configuration
   */
  public TrajectoryConfig getTrajectoryConfig() {
    return this.trajectoryConfig;
  }

  /**
   * Generate a command for following a trajectory.
   * 
   * @param trajectory the trajectory to follow in the command
   * @return the command that follows the path
   */
  public MecanumControllerCommand trajectoryCommand(Trajectory trajectory) {
    return new MecanumControllerCommand(
      trajectory, // Path to follow
      this::getPose, // Current robot position

      KINEMATICS, // Distance from center of robot to each wheel

      new PIDController(0, 0, 0), // PID controller on x-position
      new PIDController(0, 0, 0), // PID controller on y-position
      rotationController, // PID controller on rotation

      MAX_SPEED, // Maximum speed in m/s

      this::drive, // Method pointer to voltage output
      this // Command dependencies
    );
  }

  public void toggleFieldOriented() {
    fieldOriented = !fieldOriented;
  }

  private boolean getFieldOriented() {
    return fieldOriented;
  }

  public void toggleSlowMode() {
    //If speedMultiplier is not on full speed, it sets it full speed and the inverse
    speedMultiplier = (speedMultiplier == 0.8) ? 0.4 : 0.8;
  }

  /**
   * @return the current multiplier for the robot speed, used for slow mode.
   */
  private double getSpeedMultiplier() {
    return speedMultiplier;
  }

  /**
   * This method rotates clockwise if targetAngle is between 0 and 180 degrees, and rotates counterclockwise if targetAngle is between 181 and 360 degrees.
   * It also updates the currentAngle variable to the new angle after rotating.
   * @param targetAngle takes an angle between 0-360 degrees
   */
  public void rotateToAngle(double targetAngle)
  {
    double desiredAngle = targetAngle;
    double currentAngle = gyro.getAngle();
    
    if(currentAngle <= (desiredAngle + 5.0) || currentAngle >= (desiredAngle - 5.0))
    {
        if(desiredAngle <= 180.0)
      {
        this.drive(0, 0, 0.4);
      }
      else if(desiredAngle > 180.0)
      {
        this.drive(0, 0, -0.4);
      }
    }

    currentAngle = gyro.getAngle();
  }

  /**
   * Returns the angle given by the gyro
   */
  public double getGyro() {
    return gyro.getAngle();
  }
  
  /**
   * Method that allows the robot to drive while targeting (cargo or reflective tape)
   * @param X x speed of robot
   * @param Y y speed of robot
   * @param targetAngle Angle, in degrees, from camera
   */
  public void driveWithTargeting(double x, double y, double targetAngle) {
    drive(x / 2, y / 2, (rotationController.calculate(getGyro(), getGyro() - targetAngle)));
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    odometry.update(
      new Rotation2d(gyro.getAngle()),
      getWheelSpeeds()
    );
  }

  @Override
  public void initSendable(SendableBuilder builder) {
    builder.setSmartDashboardType("DriveSystem");
    builder.addBooleanProperty("Field Oriented", this::getFieldOriented, null);
    builder.addDoubleProperty("Speed Multiplier", this::getSpeedMultiplier, null);
  }
}
