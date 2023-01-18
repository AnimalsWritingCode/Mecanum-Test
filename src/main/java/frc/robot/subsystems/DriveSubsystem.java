// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.MecanumDriveKinematics;
import edu.wpi.first.math.kinematics.MecanumDriveOdometry;
import edu.wpi.first.math.kinematics.MecanumDriveWheelPositions;
import edu.wpi.first.math.kinematics.MecanumDriveWheelSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import java.util.function.Supplier;

import com.ctre.phoenixpro.configs.TalonFXConfiguration;
import com.ctre.phoenixpro.controls.VelocityTorqueCurrentFOC;
import com.ctre.phoenixpro.hardware.Pigeon2;
import com.ctre.phoenixpro.hardware.TalonFX;
import com.ctre.phoenixpro.signals.NeutralModeValue;

public class DriveSubsystem extends SubsystemBase {
  private static final double kMaxSpeed = 8.0;
  private static final double kWheelDiameter = 0.1524;
  private static final double kGearRatio = 72 / 13;
  private static final double kSpeedToRotationsMultiplier = (1 / (Math.PI * kWheelDiameter)) * kGearRatio;

  private final TalonFX frontLeftMotor = new TalonFX(1);
  private final TalonFX frontRightMotor = new TalonFX(2);
  private final TalonFX rearLeftMotor = new TalonFX(3);
  private final TalonFX rearRightMotor = new TalonFX(4);

  private final Translation2d frontLeftLocation = new Translation2d(0.381, 0.381);
  private final Translation2d frontRightLocation = new Translation2d(0.381, -0.381);
  private final Translation2d rearLeftLocation = new Translation2d(-0.381, 0.381);
  private final Translation2d rearRightLocation = new Translation2d(-0.381, -0.381);

  private final MecanumDriveKinematics kinematics = new MecanumDriveKinematics(
      frontLeftLocation, frontRightLocation, rearLeftLocation, rearRightLocation);

  private final Pigeon2 gyro = new Pigeon2(0);

  private final Supplier<Double> frontLeftVelocity = frontLeftMotor.getVelocity().asSupplier();
  private final Supplier<Double> frontRightVelocity = frontRightMotor.getVelocity().asSupplier();
  private final Supplier<Double> rearLeftVelocity = rearLeftMotor.getVelocity().asSupplier();
  private final Supplier<Double> rearRightVelocity = rearRightMotor.getVelocity().asSupplier();

  private final Supplier<Double> frontLeftPosition = frontLeftMotor.getPosition().asSupplier();
  private final Supplier<Double> frontRightPosition = frontRightMotor.getPosition().asSupplier();
  private final Supplier<Double> rearLeftPosition = rearLeftMotor.getPosition().asSupplier();
  private final Supplier<Double> rearRightPosition = rearRightMotor.getPosition().asSupplier();

  private final VelocityTorqueCurrentFOC torqueVelocity = new VelocityTorqueCurrentFOC(0, 0, 0, false);

  private final MecanumDriveOdometry odometry = new MecanumDriveOdometry(kinematics, gyro.getRotation2d(),
      getCurrentPositions());

  /** Creates a new DriveSubsystem. */
  public DriveSubsystem() {
    gyro.reset();

    TalonFXConfiguration configs = new TalonFXConfiguration();

    /*
     * Torque-based velocity does not require a feed forward, as torque will
     * accelerate the rotor up to the desired velocity by itself
     */
    configs.Slot0.kP = 5; // An error of 1 rotation per second results in 5 amps output
    configs.Slot0.kI = 0.1; // An error of 1 rotation per second increases output by 0.1 amps every second
    configs.Slot0.kD = 0.001; // A change of 1000 rotation per second squared results in 1 amp output

    // Peak output of 40 amps
    configs.TorqueCurrent.PeakForwardTorqueCurrent = 40;
    configs.TorqueCurrent.PeakReverseTorqueCurrent = -40;

    configs.MotorOutput.NeutralMode = NeutralModeValue.Brake;

    frontLeftMotor.getConfigurator().apply(configs);
    frontRightMotor.getConfigurator().apply(configs);
    rearLeftMotor.getConfigurator().apply(configs);
    rearRightMotor.getConfigurator().apply(configs);

    frontRightMotor.setInverted(true);
    rearRightMotor.setInverted(true);
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    updateOdometry();
  }

  public MecanumDriveWheelSpeeds getCurrentState() {
    return new MecanumDriveWheelSpeeds(
        frontLeftVelocity.get(),
        frontRightVelocity.get(),
        rearLeftVelocity.get(),
        rearRightVelocity.get());
  }

  public MecanumDriveWheelPositions getCurrentPositions() {
    return new MecanumDriveWheelPositions(
        frontLeftPosition.get(),
        frontRightPosition.get(),
        rearLeftPosition.get(),
        rearRightPosition.get());
  }

  public void setSpeeds(MecanumDriveWheelSpeeds speeds) {
    frontLeftMotor
        .setControl(torqueVelocity.withVelocity(speeds.frontLeftMetersPerSecond * kSpeedToRotationsMultiplier));
    frontRightMotor
        .setControl(torqueVelocity.withVelocity(speeds.frontRightMetersPerSecond * kSpeedToRotationsMultiplier));
    rearLeftMotor
        .setControl(torqueVelocity.withVelocity(speeds.rearLeftMetersPerSecond * kSpeedToRotationsMultiplier));
    rearRightMotor
        .setControl(torqueVelocity.withVelocity(speeds.rearRightMetersPerSecond * kSpeedToRotationsMultiplier));
  }

  public void drive(double x, double y, double z, boolean fieldRelative) {
    MecanumDriveWheelSpeeds mecanumDriveWheelSpeeds = kinematics.toWheelSpeeds(
        fieldRelative
            ? ChassisSpeeds.fromFieldRelativeSpeeds(x, y, z, gyro.getRotation2d())
            : new ChassisSpeeds(x, y, z));
    mecanumDriveWheelSpeeds.desaturate(kMaxSpeed);
    setSpeeds(mecanumDriveWheelSpeeds);
  }

  public void updateOdometry() {
    odometry.update(gyro.getRotation2d(), getCurrentPositions());
  }

  public Command driveCommand(double x, double y, double z, boolean fieldRelative) {
    return new RunCommand(() -> drive(x, y, z, fieldRelative), this);
  }
}