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

public class EmergencyAgent extends Agent{
    protected final Logger log = LoggerFactory.getLogger(getClass());

    VehicleParameters myParameters;
    SignParameters mySignParameters;

    Ontology ontology = ParametersOntology.getInstance();

    private static final String AGENT_TYPE = "emergency_agent";

    private HashMap<AID, VehicleParameters> otherCarsParams = new HashMap<>();
    private HashMap<AID, SignParameters> allSignsParams = new HashMap<>();


    @Override
    // setup emergency agent with specific parameters
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

        myParameters = new VehicleParameters(speed, MaxSpeed, 2L);

        addBehaviour(new EmergencyAgent.Receiver());
        //  addBehaviour(new CreateNewCar(this, 3000));
        addBehaviour(new EmergencyAgent.UpdateParameters(this, 100));

        GUIApp.onSetupEmergency(getAID(), myParameters.getX());
    }

    class CreateNewCar extends WakerBehaviour {

        public CreateNewCar(Agent a, int period) {
            super(a, period);
        }

        @Override
        protected void handleElapsedTimeout() {
            String[] args = {"speed:100"};
            try {
                AgentController ac = getContainerController().createNewAgent("SzybszyAgent", EmergencyAgent.class.getName(), args);
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

    // cyclic update parameters of emergency agent
    class UpdateParameters extends TickerBehaviour {

        public UpdateParameters(Agent a, int period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            VehicleParameters front_vehicle = null;
            Long front_vehicle_number = Long.MAX_VALUE;

            //tutaj for mo calej mapie znakow
            //interesuje nas znak o pozycji Y najblizszej naszej
            SignParameters default_sign = new SignParameters(0L, Long.MAX_VALUE,Long.MAX_VALUE );
            SignParameters closed_sign = default_sign;
            SignParameters closed_front_sign = default_sign;
            Long difference_y_behind_sign = Long.MIN_VALUE;
            Long difference_y_front_sign = Long.MAX_VALUE;

            //przyjete zalozenie - zakresy znakow nie moga na siebie nachodzic
            for (Map.Entry<AID, SignParameters> sign : allSignsParams.entrySet()) {

                SignParameters sign_parameters = sign.getValue();
                Long difference_y = sign_parameters.getYBegin() - myParameters.getY();

                if(difference_y <= 0) {
                    //jesli mniejsze od 0 lub rowne 0 to znak jest juz miniety przez samochod = obowiazuje
                    //im wartosc bardziej blizsza 0 tym bardzieje aktualny znak - czyli potrzeba sprawdzać ktory znak ma najwikeszy diff
                    //ten bedzie obowiazywac
                    if(difference_y > difference_y_behind_sign) {
                        Long difference_y_to_end = sign_parameters.getYEnd() - myParameters.getY();

                        if(difference_y_to_end > 0) {
                            //jesli koniec znaku jest jeszcze nie miniety
                            difference_y_behind_sign = difference_y;
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


            for (Map.Entry<AID, VehicleParameters> vehicle : otherCarsParams.entrySet()) {

                VehicleParameters vehicle_parameters = vehicle.getValue();
                Long difference_y = vehicle_parameters.getY() - myParameters.getY();
                if (Objects.equals(vehicle_parameters.getX(), myParameters.getX())) {
                    if (difference_y < front_vehicle_number && difference_y >= 0) {
                        front_vehicle_number = difference_y;
                        front_vehicle = vehicle_parameters;
                    }
                    if (difference_y == 0) {
                        log.error("Samochody w tym samym miejscu");
                    }
                }
            }

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

            if (myParameters.getX() == 2L)
            {
                    if(front_vehicle == null || can_move_on){
                        if(myParameters.getSpeed() >= myParameters.getMaxSpeed()){
                            myParameters.setSpeed(myParameters.getMaxSpeed());
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);

                        }else{
                            myParameters.addPercentageAcceleration(10L);
                            myParameters.updateSpeed();
                            myParameters.updateY(timeInterval);
                        }
                    }else{
                        if(Objects.equals(myParameters.getSpeed(), front_vehicle.getSpeed())){
                            myParameters.setAcceleration(0L);
                            myParameters.updateY(timeInterval);
                        }else{
                            myParameters.setPercentageAcceleration(-20L);
                            myParameters.updateSpeed();
                            myParameters.updateY(timeInterval);
                        }
                    }
            }

                AID found_ahead = null;
                Boolean was_found_ahead = false;
                for (Map.Entry<AID, VehicleParameters> vehicle : otherCarsParams.entrySet()) {

                    AID aid = vehicle.getKey();
                    VehicleParameters vehicle_parameters = vehicle.getValue();

                    if (vehicle_parameters == front_vehicle) {
                        found_ahead = aid;
                        was_found_ahead = true;
                    }
                }

                if (was_found_ahead) {
                    SendEmergencyRequest(found_ahead);
                }

            System.out.println("Parametrey dla: " + getName() + "\t to: Predkosc:  " + myParameters.getSpeed() + ",\t X: " + myParameters.getX() + ",\t Y: " + myParameters.getY());
            SendParameters();

            if (myParameters.getY() >= CarsApplication.MAX_Y) {
                doDelete();
                GUIApp.onDeleteEmergency(getAID());
            }

            GUIApp.onUpdateParametersEmergency(getAID(), EmergencyAgent.this.myParameters.getX(), EmergencyAgent.this.myParameters.getY());
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
                    }

                    if(action instanceof PleaseRequest)
                    {
                        PleaseRequest please_request = (PleaseRequest) action;

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
    //sending parameters of agent
    private void SendParameters() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        List<AID> receivers = CarsMap.getAllOtherCars(this, AGENT_TYPE);
        AID receiver = new AID("SzybszyAgent", AID.ISLOCALNAME);
        for (AID recv : receivers) {
            msg.addReceiver(recv);
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
    //sending request to free the line before EmergencyAgent
    private void SendEmergencyRequest(AID receivingAgent) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        AID receiver = new AID("AgentCar", AID.ISLOCALNAME);

        msg.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
        msg.setOntology(ParametersOntology.NAME);
        msg.addReceiver(receivingAgent);


        EmergencyRequest eRequest;

        //EmergencyAgent Request
            eRequest = new EmergencyRequest("zwolnij pas");
        try {
            getContentManager().fillContent(msg, new Action(receiver, eRequest));
        } catch (Codec.CodecException | OntologyException e) {
            e.printStackTrace();
        }
        send(msg);
    }
}
