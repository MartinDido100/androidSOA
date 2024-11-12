#include <SoftwareSerial.h>

#define ON 1
#define	ARMED 2
#define	ACTIVATED 3
#define TIEMPO_MAX_MS 100

#define DISTANCIA_MAX_DETECCION 50
#define DISTANCIA_MIN_DETECCION 0

#define BTStateValue 1

//Defino los tipos de eventos que pueden suceder
enum tipoEvento {
    PUSH_BUTTON,
    MOVEMENT_DETECTED,
    PWM_LED,
  	TIMEOUT_TIMER,
    NO_EVENT
};
const int YELLOWLED = 5;//pin YELLOWLED
const int GREENLED = 7;//pin GREENLED
const int REDLED = 6;//pin REDLED
const int PULSADOR = 2;//pinPulsador
const int SENSORTRIGGER = 9;//pinTrigger
const int SENSORECHO = 8;//pinEcho
const int BUZZER = 3;//pin Buzzer
const int REEDSWITCH = 4; //pin reed switch
const float TIME_TO_DISTANCE_CONVERSION = 0.01723; //Conversion de tiempo a distancia para el sensor de ultrasonido
const int TRIGGER_RESET_TIME = 2;       // Tiempo para resetear el trigger (en microsegundos)
const int TRIGGER_PULSE_TIME = 10;      // Duración del pulso de activación (en microsegundos)


unsigned long tiempo_actual;
unsigned long tiempo_anterior;
int valorPul = 0; //valorPul se emplea para almacenar el estado del boton
int valLedAma = 0; // 0 LED apagado, mientras que 1 encendido
int valGREENLED = 0; // 0 LED apagado, mientras que 1 encendido
int valorPulAnterior = 0; // almacena el antiguo valor de val
int distSensorDist; //distancia del sensor distancia
int duracionSensorDist; ////Valores para la lectura almacenamineto y conversion
int current_state; // estado actual de la maquina de estados
int valorPwmAnterior; //valor anterior del PWM
tipoEvento evento = NO_EVENT; // no sucedio ningun evento al inicio
bool activeTimer=false;
bool botonPresionado = false;
char datoBT = ' ';

SoftwareSerial BTserial(11,10);

//Declaracion de funciones
void setup()
{
  initt();
}

// Initializo el sistema
void initt()
{
  	pinMode(REDLED,OUTPUT); // establecer que el pin digital es una señal de salida
  	pinMode(YELLOWLED,OUTPUT); // establecer que el pin digital es una señal de salida
    pinMode(GREENLED,OUTPUT); // establecer que el pin digital es una señal de salida
    pinMode(PULSADOR,INPUT); // boton como señal de entrada
    pinMode(SENSORTRIGGER,OUTPUT);//es outpult porque eltrigger es el que emite el sonido
    pinMode(SENSORECHO,INPUT); // es input porque el echo es el que recibe el sonido
    pinMode(BUZZER,OUTPUT); // establecer que el pin digital es una señal de salida
  	pinMode(A0,INPUT); // establecer que el pin analogico es una señal de entrada
	 pinMode(REEDSWITCH,INPUT); // establecer que el pin digital es una señal de entrada
  	current_state = ON; // inicializo la maquina de estados en el estado ON
  	tiempo_actual = millis(); //tomo el tiempo actual
    valorPwmAnterior = 0;
  	BTserial.begin(9600);  //Configuro la velocidad de transferencia BT
    Serial.begin(9600);
    BTserial.write("Alarma encendida\n");
}

void setPwmValue(){
  analogWrite(REDLED,analogRead(A0));
}

long readDistance()
{
    digitalWrite(SENSORTRIGGER, LOW);
    delayMicroseconds(TRIGGER_RESET_TIME);
    digitalWrite(SENSORTRIGGER, HIGH);
    delayMicroseconds(TRIGGER_PULSE_TIME);
    digitalWrite(SENSORTRIGGER, LOW);
    return (pulseIn(SENSORECHO, HIGH) * TIME_TO_DISTANCE_CONVERSION);
}

void activateBuzzer(){
	tone(BUZZER,1);
}

void disableBuzzer(){
  noTone(BUZZER);
}

void sendCurrentState() {
    switch (current_state) {
        case ON:
            BTserial.write("ON\n");
            break;
        case ARMED:
            BTserial.write("ARMED\n");
            break;
        case ACTIVATED:
            BTserial.write("ACTIVATED\n");
            break;
    }
}

void loop()
{
  fsm();
}

// Manejador de eventos
void handle_event()
{
    //Manejo Evento Push button
    valorPul= digitalRead(PULSADOR); // lee el estado del Boton
  
    if (valorPul == HIGH && valorPulAnterior == LOW && botonPresionado == false)//recibe alto si lo presiono
    {
        botonPresionado = true;
        evento = PUSH_BUTTON;
      	return;
    }

    valorPulAnterior = valorPul;

    if(valorPul == LOW){
      botonPresionado = false;
    }


    //Manejo evento PWM
    int valorPWM = analogRead(A0); 

    if(valorPWM != valorPwmAnterior)
    {
      evento = PWM_LED;
      valorPwmAnterior = valorPWM;
      return;
    }


    //Manejo evento movimiento detectado
  	if(current_state==ARMED){
      	long dist = readDistance();
        if((dist > DISTANCIA_MIN_DETECCION && dist < DISTANCIA_MAX_DETECCION)){
      		evento = MOVEMENT_DETECTED;
          BTserial.write("ACTIVATEDM\n");
          return;
        }

      	if(digitalRead(REEDSWITCH) == LOW){
            evento = MOVEMENT_DETECTED;
          	BTserial.write("ACTIVATEDR\n");
          	return;
      	}
    }
      
 	//Manejo del timer para titilar
    if(activeTimer == true && current_state == ACTIVATED){
        tiempo_actual=millis();
        if(tiempo_actual - tiempo_anterior >= TIEMPO_MAX_MS){
            tiempo_anterior = tiempo_actual;
            evento = TIMEOUT_TIMER;
            return;
        }
    }
  
  	if (BTserial.available()){
        datoBT = BTserial.read();
        Serial.println(datoBT);
        analizarDato(datoBT);
    }
}

void analizarDato(char datoBT)
{
   if(datoBT==BTStateValue) //Se apreto el pulsador
   {
      evento = PUSH_BUTTON;
   }else{
    sendCurrentState();
   }
}


void reset_event()
{
    evento=NO_EVENT;
}

//Enciende el led pasado por parametro
void turnOnLed(int pin){
    digitalWrite(pin,HIGH);
}

// Apaga el led pasado por parametro
void turnOffLed(int pin){
    digitalWrite(pin,LOW);
}

//Prepara la deteccion de alarma
void prepareAlarmDetection(){
    BTserial.write("Alarma en escucha\n");
    turnOnLed(YELLOWLED);
    current_state=ARMED;
}

//Activa la deteccion de alarma
void disableAlarmDetection(){
    turnOffLed(YELLOWLED);
    BTserial.write("Alarma apagada\n");
    current_state=ON;
}

//Activa la alarma
void activateAlarm(){
  turnOffLed(YELLOWLED);
  current_state=ACTIVATED;
  activateBuzzer();
}

//Desactiva la alarma
void disableAlarm(){
    turnOffLed(YELLOWLED);
    turnOffLed(GREENLED);
    disableBuzzer();
    BTserial.write("Alarma desactivada\n");
    current_state = ON;
}

//Hace parpadear el led verde
void twinkleGreenLed(){
  digitalWrite(GREENLED,!digitalRead(GREENLED));
}

void fsm()
{
  handle_event();
  switch(current_state)
  {
    case ON: 
        switch(evento)
        {
            case PUSH_BUTTON:
                prepareAlarmDetection();
                break;
            case PWM_LED:
                setPwmValue();
          	break;
        }
    break;
    case ARMED:
        switch(evento)
        {
            case PUSH_BUTTON:
                disableAlarmDetection();
            break;
            case MOVEMENT_DETECTED:
                activateAlarm();
                activeTimer = true;
            break;
        }
    break;
    case ACTIVATED:
        switch(evento)
        {
            case PUSH_BUTTON:
                disableAlarm();
                activeTimer = false;
            break;
            case TIMEOUT_TIMER:
                twinkleGreenLed();
            break;
            case NO_EVENT:
            break;
        }
  }
  reset_event();
}