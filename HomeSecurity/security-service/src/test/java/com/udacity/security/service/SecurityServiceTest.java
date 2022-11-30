package com.udacity.security.service;

import static com.udacity.security.data.AlarmStatus.ALARM;
import static com.udacity.security.data.AlarmStatus.NO_ALARM;
import static com.udacity.security.data.AlarmStatus.PENDING_ALARM;
import static com.udacity.security.data.ArmingStatus.ARMED_AWAY;
import static com.udacity.security.data.ArmingStatus.ARMED_HOME;
import static com.udacity.security.data.ArmingStatus.DISARMED;
import static com.udacity.security.data.SensorType.DOOR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.udacity.image.service.ImageService;
import com.udacity.security.data.AlarmStatus;
import com.udacity.security.data.ArmingStatus;
import com.udacity.security.data.SecurityRepository;
import com.udacity.security.data.Sensor;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

  private SecurityService securityService;
  private BufferedImage bufferedImage;
  private Sensor sensor;

  @Mock
  private SecurityRepository securityRepository;

  @Mock
  private ImageService imageService;

  private static Stream<Arguments> sensorActiveStatus() {
    return Stream.of(
        Arguments.of(true),
        Arguments.of(false)
    );
  }

  private static Stream<Arguments> armingStatus() {
    return Stream.of(
        Arguments.of(ARMED_HOME),
        Arguments.of(ARMED_AWAY)
    );
  }

  @BeforeEach
  void init() {
    securityService = new SecurityService(securityRepository, imageService);
    sensor = new Sensor("sensor", DOOR);
  }

  // 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenAlarmArmedAndSensorActivated_pending(ArmingStatus armingStatus) {
    when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
    when(securityRepository.getAlarmStatus()).thenReturn(NO_ALARM);
    securityService.changeSensorActivationStatus(sensor, true);
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), PENDING_ALARM);
  }

  // 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm,
  // set the alarm status to alarm.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenAlarmArmedAndSensorActivatedAndStatusPending_alarm(ArmingStatus armingStatus) {
    when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    securityService.changeSensorActivationStatus(sensor, true);
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository, atMost(2)).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), NO_ALARM);
  }

  // 3. If pending alarm and all sensors are inactive, return to no alarm state.
  @Test
  void whenPendingAlarmAndAllSensorsInactive_noAlarm() {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    securityService.changeSensorActivationStatus(sensor, false);
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), NO_ALARM);
  }

  // 4. If alarm is active, change in sensor state should not affect the alarm state.
  @ParameterizedTest
  @MethodSource("sensorActiveStatus")
  void whenAlarmActiveIfSensorDeactivated_noSensorChange(boolean status) {
    sensor.setActive(status);
    when(securityRepository.getAlarmStatus()).thenReturn(ALARM);
    securityService.changeSensorActivationStatus(sensor, !status);
    ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
    verify(securityRepository, never()).setAlarmStatus(any());
    verify(securityRepository, atMostOnce()).updateSensor(captor.capture());
    assertEquals(captor.getValue(), sensor);
  }

  // 5. If a sensor is activated while already active and the system is in pending state,
  // change it to alarm state.
  @Test
  void whenSensorActivatedAndStatusPending() {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    securityService.changeSensorActivationStatus(sensor, true);
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository, atMost(2)).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), NO_ALARM);
  }

  // 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
  @Test
  void whenSensorDeactivatedAndStatusPending_noAlarmStateChange() {
    securityService.changeSensorActivationStatus(sensor, false);
    verify(securityRepository, never()).setAlarmStatus(any());
  }

  // 7. If the image service identifies an image containing a cat while the system is armed-home,
  // put the system into alarm status.
  // Test case 7 is about having the house armed and then getting an image of cat.
  @Test
  void whenImageServiceIdentifiesCat_alarm() {
    when(securityRepository.getArmingStatus()).thenReturn(ARMED_HOME);
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
    bufferedImage = new BufferedImage(1, 1, TYPE_INT_RGB);
    securityService.processImage(bufferedImage);
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), ALARM);
  }

  // 8. If the image service identifies an image that does not contain a cat,
  // change the status to no alarm as long as the sensors are not active.
  @Test
  void whenImageServiceIdentifiesNoCat_noAlarm() {
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
    bufferedImage = new BufferedImage(1, 1, TYPE_INT_RGB);
    securityService.processImage(bufferedImage);
    verify(securityRepository).setAlarmStatus(NO_ALARM);
  }

  // 9. If the system is disarmed, set the status to no alarm.
  @Test
  void whenSystemDisarmed_noAlarm() {
    securityService.setArmingStatus((DISARMED));
    ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
    verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
    assertEquals(captor.getValue(), NO_ALARM);
  }

  // 10. If the system is armed, reset all sensors to inactive.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenSystemArmed_resetSensors(ArmingStatus armingStatus) {
    List<Sensor> sensorList = List.of(
        new Sensor("sensor1", DOOR),
        new Sensor("sensor2", DOOR)
    );
    sensorList.get(0).setActive(false);
    sensorList.get(1).setActive(true);
    Set<Sensor> sensors = new HashSet<>(sensorList);
    when(securityRepository.getSensors()).thenReturn(sensors);
    securityService.setArmingStatus(armingStatus);
    securityService.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
  }

  // 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
  // Test case 11 is about having the house unarmed first, getting an image of cat and then if you
  // put it on armed again, the alarm should go on.
  @Test
  void whenCameraShowsCat_alarm() {
    when(securityRepository.getArmingStatus()).thenReturn(DISARMED);
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
    bufferedImage = new BufferedImage(1, 1, TYPE_INT_RGB);
    securityService.processImage(bufferedImage);
    securityService.setArmingStatus(ARMED_HOME);
    verify(securityRepository).setAlarmStatus(ALARM);
  }
}