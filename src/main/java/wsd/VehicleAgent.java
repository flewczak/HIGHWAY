package wsd;

import app.CarsApplication;
import graphics.GUIApp;
import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import ontology.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.lang.System;


public class VehicleAgent extends Agent {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    VehicleParameters myParameters;

    SignParameters mySignParameters;

    Boolean mustFreeLane;
    Boolean FreeLaneDone;
    Boolean slowDownToLetIn;
    AID whoToAskToLetMeIn;
    long timerStart;
    long timerEnd;
    Boolean ERReceived;

    Ontology ontology = ParametersOntology.getInstance();

    private static final String AGENT_TYPE = "vehicle_agent";

    private HashMap<AID, VehicleParameters> otherCarsParams = new HashMap<>();
    private HashMap<AID, SignParameters> allSignsParams = new HashMap<>();

    // setup of VehicleAgent with specific parameters
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args.length != 2)
            throw new IllegalStateException("Needs more arguments");
        Long speed = Long.parseLong(args[0].toString().split(":")[1]);
        Long MaxSpeed = Long.parseLong(args[1].toString().split(":")[1]);
        System.out.println("Utworzono Agenta: " + getName() + ", Predkosc: " + speed);
        getContentManager().registerLanguage(new SLCodec(), FIPANames.ContentLanguage.FIPA_SL0);
        getContentManager().registerOntology(ontology);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(AGENT_TYPE);
        sd.setName(getName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        mustFreeLane = false;
        FreeLaneDone = false;
        slowDownToLetIn = false;
        timerStart = 0L;
        timerEnd = 0L;
        ERReceived = false;

        myParameters = new VehicleParameters(speed, MaxSpeed, 1L);

        addBehaviour(new Receiver());
        //  addBehaviour(new CreateNewCar(this, 3000));
        addBehaviour(new UpdateParameters(this, 100));

        GUIApp.onSetup(getAID(), myParameters.getX());
    }

    class CreateNewCar extends WakerBehaviour {

        public CreateNewCar(Agent a, int period) {
            super(a, period);
        }

        @Override
        protected void handleElapsedTimeout() {
            String[] args = {"speed:100"};
            try {
                AgentController ac = getContainerController().createNewAgent("SzybszyAgent", VehicleAgent.class.getName(), args);
                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            AID receiver = new AID("SzybszyAgent", AID.ISLOCALNAME);
            msg.addReceiver(receiver);
            msg.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
            msg.setOntology(ParametersOntology.NAME);
            try {
                myAgent.getContentManager().fillContent(msg, new Action(receiver, myParameters));
            } catch (Codec.CodecException | OntologyException e) {
                e.printStackTrace();
            }
            send(msg);
        }
    }

    // cyclic update parameters of VehicleAgent
    class UpdateParameters extends TickerBehaviour {

        public UpdateParameters(Agent a, int period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            VehicleParameters front_vehicle = null;
            Long front_vehicle_number = Long.MAX_VALUE;
            VehicleParameters front_beside_vehicle = null;
            Long front_beside_vehicle_number = Long.MAX_VALUE;
            VehicleParameters behind_beside_vehicle = null;
            Long behind_beside_vehicle_number = Long.MAX_VALUE;

            //tutaj for mo calej mapie znakow
            //interesuje nas znak o pozycji Y najblizszej naszej
            SignParameters default_sign = new SignParameters(0L, Long.MAX_VALUE,Long.MAX_VALUE);
            SignParameters closed_sign = default_sign;
            SignParameters closed_front_sign = default_sign;
            Long difference_y_passed_sign = Long.MIN_VALUE;
            Long difference_y_front_sign = Long.MAX_VALUE;

            //przyjete zalozenie - zakresy znakow nie moga na siebie nachodzic
            for (Map.Entry<AID, SignParameters> sign : allSignsParams.entrySet()) {
                SignParameters sign_parameters = sign.getValue();
                Long difference_y = sign_parameters.getYBegin() - myParameters.getY();

                if(difference_y <= 0) {
                    //jesli mniejsze od 0 lub rowne 0 to znak jest juz miniety przez samochod = obowiazuje
                    //im wartosc bardziej blizsza 0 tym bardzieje aktualny znak - czyli potrzeba sprawdzać ktory znak ma najwikeszy diff
                    //ten bedzie obowiazywac
                    if(difference_y > difference_y_passed_sign) {
                        Long difference_y_to_end = sign_parameters.getYEnd() - myParameters.getY();

                        if(difference_y_to_end > 0) {
                            //jesli koniec znaku jest jeszcze nie miniety
                            difference_y_passed_sign = difference_y;
                            closed_sign = sign_parameters;
                        }
                    }
                }
                else {
                    //jesli wieksze od 0 to znak jest jeszcze nie miniety przez samochod = nie obowiazuje
                    //zostaje poprzedni
                    //trzeba zainicjowac jakims znakeim domyslnym na wypadek gdyby wszystkie znaki byly przed
                    if(difference_y < difference_y_front_sign) {
                        //jesli znak jest przed autem i blizej niz poprzedni zapisany to zapisz go
                        difference_y_front_sign = difference_y;
                        closed_front_sign = sign_parameters;
                    }
                }
            }
            myParameters.setMaxSpeedOfSign(closed_sign.getLimitMaxSpeed());

             System.out.println("dane znaku najblizszego za autem o nazwie  " + getName()+ "pamrametry:" +  closed_sign.getYBegin() +"  "+closed_sign.getYEnd()+ "  "+closed_sign.getLimitMaxSpeed());
             System.out.println("dane znaku najblizszego przed autem  " + getName() + "pamrametry:" + closed_front_sign.getYBegin() +"  "+closed_front_sign.getYEnd()+ "  "+closed_front_sign.getLimitMaxSpeed());

            Long minimal_distance = 100L;
            //test czy znak jest wystarczajaco blisko by zaczac zwalniac
            Long temp = closed_front_sign.getYBegin() - myParameters.getY();
            Boolean sign_close_by = (temp < minimal_distance) && (temp >0);

            for (Map.Entry<AID, VehicleParameters> vehicle : otherCarsParams.entrySet()) {
                AID aid = vehicle.getKey();
                VehicleParameters vehicle_parameters = vehicle.getValue();
                Long diff = vehicle_parameters.getY() - myParameters.getY();
                if (Objects.equals(vehicle_parameters.getX(), myParameters.getX())) {
                    if (diff < front_vehicle_number && diff >= 0) {
                        front_vehicle_number = diff;
                        front_vehicle = vehicle_parameters;
                    }
                    if (diff == 0) {
                        log.error("Samochody w tym samym miejscu");
                    }
                } else {
                    if (diff < front_beside_vehicle_number && diff >= 0) {
                        front_beside_vehicle_number = diff;
                        front_beside_vehicle = vehicle_parameters;
                    }
                    if (-diff < behind_beside_vehicle_number && diff < 0) {
                        behind_beside_vehicle_number = -diff;
                        behind_beside_vehicle = vehicle_parameters;

                        whoToAskToLetMeIn = aid;
                    }
                    if (diff == 0) {
                        log.error("Dziwny przypadek");
                    }
                }
            }

            Boolean can_change_lane = false;

            if(front_beside_vehicle == null){
                if(behind_beside_vehicle == null){
                    can_change_lane = true;
                }else if(myParameters.getSpeed()>= behind_beside_vehicle.getSpeed()){
                    can_change_lane = myParameters.getY() - behind_beside_vehicle.getY() - 2 * behind_beside_vehicle.getSpeed() >= 0;

                }else{
                    can_change_lane = myParameters.getY() - behind_beside_vehicle.getY() - 3 * behind_beside_vehicle.getSpeed() >= 0;
                }
            }else{
                if(behind_beside_vehicle == null) {
                    if (myParameters.getSpeed() < front_beside_vehicle.getSpeed()) {
                        can_change_lane = front_beside_vehicle.getY() - myParameters.getY() - 2 * myParameters.getSpeed() >= 0;

                    } else {
                        can_change_lane = front_beside_vehicle.getY() - myParameters.getY() - 3 * myParameters.getSpeed() >= 0;
                    }
                }else{
                    if(myParameters.getY() - behind_beside_vehicle.getY() - 3 * behind_beside_vehicle.getSpeed() >= 0 &&
                            front_beside_vehicle.getY() - myParameters.getY() - 3 * myParameters.getSpeed() >= 0){
                        can_change_lane = true;
                    }
                }
            }


            //zeby nie staraly sie wyprzedzac innych samochodow gdy EmergencyAgent jest na lewym
            //sprawdzic inne podejscia do problemu
            if(ERReceived && myParameters.getX() == 1L)
                timerStart = System.nanoTime();

            timerEnd = System.nanoTime();

            long elapsedTime = timerEnd - timerStart;
            double seconds = (double)elapsedTime / 1000000000.0;

            if(seconds < 3)
                can_change_lane = false;

            Boolean can_move_on = false;

            if(front_vehicle == null){
                can_move_on = true;
            }else{
                if(front_vehicle.getSpeed() >= myParameters.getSpeed()){
                    can_move_on = true;
                }else if (front_vehicle.getY() - myParameters.getY() - 3 * myParameters.getSpeed() >= 0) {
                    can_move_on = true;
                }
            }

            Long timeInterval = 10L;

            if(mustFreeLane) slowDownToLetIn = false;
            else if(slowDownToLetIn) mustFreeLane = false;

            if (!(myParameters.getX() == 2L)) { //jestem na prawym pasie
                if(slowDownToLetIn)
                {
                    myParameters.addSpeed(-myParameters.getSpeed() / 10);
                    if(myParameters.getSpeed() <= 0L)
                    {   myParameters.setSpeed(0L);
                        myParameters.setAcceleration(5L);
                    }
                }
                else if(front_vehicle == null || can_move_on){
                    if (sign_close_by) {
                        if (myParameters.getSpeed() >= closed_front_sign.getLimitMaxSpeed()) {
                            //jesli trzeba zwolinic bo aktualna predkosc jest wieksza od tej na zblizajacym sie znaku
                            myParameters.addPercentageAcceleration(-10L);
                            myParameters.updateSpeed();
                            myParameters.updateY(timeInterval);

                        }
                        else {
                            //dostosowywanie predkosi gdy jest przed znakiem

                            myParameters.setAcceleration(0L);
                            if (myParameters.getSpeed() < closed_front_sign.getLimitMaxSpeed()) {
                                myParameters.addPercentageAcceleration(10L);
                                myParameters.updateSpeed();

                                myParameters.updateY(timeInterval);

                            }
                            else {
                                myParameters.setAcceleration(0L);
                                myParameters.updateY(timeInterval);
                            }
                            //myParameters.updateY(timeInterval);
                        }
                    }
                    else {

                        if (myParameters.getSpeed() >= myParameters.getMaxSpeed()) {

                            myParameters.setSpeed(myParameters.getMaxSpeed());
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);

                        } else {
                                myParameters.addPercentageAcceleration(10L);
                                myParameters.updateSpeed();
                                myParameters.updateY(timeInterval);
                            }
                        }

                }else{

                    if(can_change_lane){
                        if(myParameters.getSpeed()>= myParameters.getMaxSpeed()){
                            myParameters.setSpeed(myParameters.getMaxSpeed());
                            myParameters.setAcceleration(0L);
                        }else{
                            myParameters.addPercentageAcceleration(10L);
                            myParameters.updateSpeed();
                        }
                        myParameters.setX(2L);
                        myParameters.updateY(timeInterval);
                    }else{
                        if(Objects.equals(myParameters.getSpeed(), front_vehicle.getSpeed())){
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);
                        }else{
                            myParameters.setPercentageAcceleration(-20L);
                            if(myParameters.getSpeed() < 0L) {
                                myParameters.setSpeed(0L);
                                myParameters.setPercentageAcceleration(5L);
                            }
                        }
                    }
                }
            }else{  //jestem na lewym pasie
                if(mustFreeLane && ((front_vehicle != null && front_vehicle_number < 2* myParameters.getSpeed() && front_vehicle.getSpeed() < myParameters.getSpeed()) || front_beside_vehicle_number< 3 * myParameters.getSpeed()))
                {
                    myParameters.addSpeed(-myParameters.getSpeed()/10);
                    if(myParameters.getSpeed() < 0L)
                        myParameters.setSpeed(0L);
                }
                else if(can_change_lane){
                    if(myParameters.getSpeed()>= myParameters.getMaxSpeed()){
                        myParameters.setSpeed(myParameters.getMaxSpeed());
                        myParameters.setAcceleration(0L);
                        myParameters.setX(1L);
                        myParameters.updateY(timeInterval);
                    }else{
                        myParameters.addPercentageAcceleration(10L);
                        myParameters.updateSpeed();
                        myParameters.setX(1L);
                        myParameters.updateY(timeInterval);
                    }
                }else {
                    if (front_vehicle == null || can_move_on) {
                        if (myParameters.getSpeed() >= myParameters.getMaxSpeed()) {
                            myParameters.setSpeed(myParameters.getMaxSpeed());
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);
                        } else {
                            myParameters.addPercentageAcceleration(10L);
                            myParameters.updateSpeed();
                            myParameters.updateY(timeInterval);
                        }
                    } else {
                        if (Objects.equals(myParameters.getSpeed(), front_vehicle.getSpeed())) {
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);
                        } else {
                            myParameters.setPercentageAcceleration(-20L);
                        }
                    }
                }
            }

            if(mustFreeLane && ((front_vehicle != null && front_vehicle_number < 2 * myParameters.getSpeed() && front_vehicle.getSpeed()< myParameters.getSpeed()) || front_beside_vehicle_number< 3 * myParameters.getSpeed()) )
                if(whoToAskToLetMeIn != null)
                    SendPleaseRequest(whoToAskToLetMeIn);

            mustFreeLane = false;
            slowDownToLetIn = false;
            if(ERReceived && myParameters.getX()==1L)
                ERReceived =false;

            System.out.println("Parametrey dla: " + getName() + "\t to: Predkosc:  " + myParameters.getSpeed() + ",\t X: " + myParameters.getX() + ",\t Y: " + myParameters.getY());
            SendParameters();


            if (myParameters.getY() >= CarsApplication.MAX_Y) {
                doDelete();
                GUIApp.onDelete(getAID());
            }

            GUIApp.onUpdateParameters(getAID(), VehicleAgent.this.myParameters.getX(), VehicleAgent.this.myParameters.getY());
        }
    }

    // cyclic receiving parameters from other VehicleAgents and EmergencyAgents
    class Receiver extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                //System.out.println(msg.toString());
                try {
                    ContentElement element = myAgent.getContentManager().extractContent(msg);
                    Concept action = ((Action) element).getAction();
                    if (action instanceof VehicleParameters) {
                        VehicleParameters v = (VehicleParameters) action;
                        otherCarsParams.put(msg.getSender(), v);
                    }
                    if (action instanceof SignParameters) {
                        SignParameters v = (SignParameters) action;
                        mySignParameters = v;
                        allSignsParams.put(msg.getSender(), v);
                    }
                    if(action instanceof EmergencyRequest)
                    {
                        EmergencyRequest emergency_request = (EmergencyRequest) action;
                        mustFreeLane = true;
                        ERReceived = true;

                        SendPleaseRequest(whoToAskToLetMeIn);
                    }
                    if(action instanceof PleaseRequest)
                    {
                        PleaseRequest please_request = (PleaseRequest) action;
                        slowDownToLetIn = true;
                    }
                } catch (Codec.CodecException | OntologyException e) {
                    e.printStackTrace();
                }
            } else {
                block();
            }
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown();
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private void SendParameters() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        List<AID> receivers = CarsMap.getAllOtherCars(this, AGENT_TYPE);
        AID receiver = new AID("SzybszyAgent", AID.ISLOCALNAME);
        //msg.addReceiver(receiver);
        for (AID rec : receivers) {
            msg.addReceiver(rec);
        }

        msg.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
        msg.setOntology(ParametersOntology.NAME);
        try {
            getContentManager().fillContent(msg, new Action(receiver, myParameters));
        } catch (Codec.CodecException | OntologyException e) {
            e.printStackTrace();
        }
        send(msg);

    }

    private void SendPleaseRequest(AID receivingAgent) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        AID receiver = new AID("AgentCar", AID.ISLOCALNAME);

        msg.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
        msg.setOntology(ParametersOntology.NAME);
        msg.addReceiver(receivingAgent);

        PleaseRequest pRequest;

         //Please Request
        pRequest = new PleaseRequest("prosze, wpusc mnie na prawy pas");
        try {
            getContentManager().fillContent(msg, new Action(receiver, pRequest));

        } catch (Codec.CodecException | OntologyException e) {
            e.printStackTrace();
        }
        send(msg);
    }
}


