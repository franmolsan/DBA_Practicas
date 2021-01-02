/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Francisco José Molina Sánchez
 */
public class Rescuer extends DroneDelMundo{
    // atributos heredados del AgenteDrone:
    
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;
    private ArrayList<Integer> posActual;
    private ArrayList<Integer> inicio;
    private static final int costeAccion = 4;
    private int anguloActual;
    private int zActual;
    private int energia;
    
    @Override
    public void setup() {
        super.setup();
        tipo = "RESCUER"; 
    }
    
    @Override
    public void plainExecute() {
        plainWithErrors();
    }

    public void plainWithErrors() {
        // Basic iteration
        switch (estado.toUpperCase()) {
            case "CHECKIN-LARVA":
                checkInLarva();
                break;
            case "SUBSCRIBE-WM":
                suscribirseWM();
                break;
            case "COMPRAR-SENSORES":
                comprar();
                break;
            case "ESPERAR-ORDEN":
                Info("Esperando orden");
                in = blockingReceive();
                Info(in.getContent());
                String accion = obtenerResultado();
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " Error en mensaje");
                    estado = "CANCEL-WM";
                    break;
                }
                if (accion.equals("turnOff")){
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                else if(accion.equals("")){
                    break;
                }
                else if(accion.equals("login")){
                    estado = "REALIZAR-LOGIN";
                    break;
                }
                else if (accion.equals("rescate")){
                    iniciarRescateObjetivos(getVectorObjetivos(resultadoComunicacion.get("vectorObjetivos").asArray()));
                }
                else{
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                
            case "REALIZAR-LOGIN":
                actualizarPosicionActual(resultadoComunicacion.get("posx").asInt(), resultadoComunicacion.get("posy").asInt());
                setInicio(resultadoComunicacion.get("posx").asInt(), resultadoComunicacion.get("posy").asInt());
                realizarLoginWM();
                informarCoachLoginRealizado();
                obtenerDatosSensores();
                Info("Datos sensores: " + in.getContent());
                moverse_pocho();
                //estado = "ESPERAR-ORDEN";
                estado = "RESCATE-FINALIZADO";
                break;
            case "RESCATE-FINALIZADO":
                Info("Rescate Finalizado");
                
                estado = "CHECKOUT-LARVA";
                break;
            case "CHECKOUT-LARVA":
                checkOutLarva();
                break;
            case "EXIT":
                exit();
                break;
        }
    }
    
    private void moverse_pocho (){
        
        // Crear objeto json
        JsonObject objeto = new JsonObject();

        String accion = "moveF";
        // añadir al objeto
        objeto.add("operation", accion);
        
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(objeto.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        Info ("Reply: " + inReplyTo);
        out.setInReplyTo(inReplyTo);
        send(out);

        ACLMessage msgRespuesta = blockingReceive();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta a accion " + accion + ": " + respuesta);
        
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Rescuer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    private ArrayList<ArrayList<Integer>> getVectorObjetivos(JsonArray array){
        ArrayList<ArrayList<Integer>> vector = new ArrayList<ArrayList<Integer>>();
       
        for (int i=0; i< array.size(); i++){
            ArrayList<Integer> posicionObjetivo = new ArrayList<Integer>();
            for (int j=0; j<(array.get(i).asArray()).size(); i++){
                posicionObjetivo.add((array.get(i).asArray()).get(j).asInt());
            }
            vector.add(posicionObjetivo);
        }
        return vector;
    }
    
    private void iniciarRescateObjetivos (ArrayList<ArrayList<Integer>> vectorObjetivos){
        realizarRuta(calcularRutaGreedy(vectorObjetivos));
    }
    
    //Distancia entre dos puntos
    private double distanciaEntreDosPuntos (int p1X, int p1Y, int p2X, int p2Y){
        int difX = Math.abs(p1X - p2X);
        int difY = Math.abs(p1Y - p2Y);
        double distancia = Math.sqrt(difX*difX + difY*difY);

        return distancia;
    }

    //Calcular la posicion Minima dentro de un array
    private int calcularMinimo(ArrayList<Double> array){
        double minimo = Double.MAX_VALUE;
        int posMin = -1;
        for (int i=0; i<array.size(); i++){
            if (array.get(i) < minimo){
                minimo = array.get(i);
                posMin = i;
            }
        }
        return posMin;
    }
    
    //Calcular ruta
    private ArrayList<ArrayList<Integer>> calcularRutaGreedy (ArrayList<ArrayList<Integer>> vectorObjetivos){
        ArrayList<ArrayList<Integer>> ruta = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> actual = new ArrayList<Integer>();
        actual.add(posActual.get(0)); //X
        actual.add(posActual.get(1)); //Y

        while (vectorObjetivos.size() > 0){
            ArrayList<Double> vectorDistancia = new ArrayList<Double>();
            for (int i=0; i<vectorObjetivos.size(); i++){
                vectorDistancia.add(distanciaEntreDosPuntos(actual.get(0), actual.get(1), vectorObjetivos.get(i).get(0), vectorObjetivos.get(i).get(1)));
            }
            int objetivoMasCercano = calcularMinimo(vectorDistancia);
            ruta.add(vectorObjetivos.get(objetivoMasCercano));
            actual.set(0, vectorObjetivos.get(objetivoMasCercano).get(0));
            actual.set(1,vectorObjetivos.get(objetivoMasCercano).get(1));
            vectorObjetivos.remove(objetivoMasCercano);
        }
        ruta.add(inicio);
        return ruta;
    }

    //Calcular movimientos ruta
    private void realizarRuta (ArrayList<ArrayList<Integer>> ruta){
        ArrayList<Integer> actual = new ArrayList<Integer>();
        actual.add(posActual.get(0)); //X
        actual.add(posActual.get(1)); //Y
        boolean rescatar = true;
        for (int i=0; i<ruta.size(); i++){
            if (i == ruta.size() - 1){
                rescatar = false;
            }
            moverseDePuntoAPunto(actual.get(0), actual.get(1), ruta.get(i).get(0), ruta.get(i).get(1), rescatar);
            actual.set(0,ruta.get(i).get(0));
            actual.set(1,ruta.get(i).get(1));
        }
    }

    //Calcular movimientos entre dos puntos
    private void moverseDePuntoAPunto (int p1X, int p1Y, int p2X, int p2Y, boolean rescatar){
        while (p1X != p2X && p1Y != p2Y){
            if (energia < 250){
                zActual = recargar(zActual);//revisar si se puede reutilizar
                energia = 1000;
            }
            if (p1X < p2X && p1Y < p2Y){
                anguloActual = 135;
                p1X ++;
                p1Y ++;
            } else if (p1X > p2X && p1Y < p2Y){
                anguloActual = -135;
                p1X --;
                p1Y ++;
            } else if (p1X < p2X && p1Y > p2Y){
                anguloActual = 45;
                p1X ++;
                p1Y --;
            } else if (p1X > p2X && p1Y > p2Y){
                anguloActual = -45;
                p1X --;
                p1Y --;
            } else if (p1X < p2X && p1Y == p2Y){
                anguloActual = 90;
                p1X ++;
            } else if (p1X > p2X && p1Y == p2Y){
                anguloActual = -90;
                p1X --;
            } else if (p1X == p2X && p1Y < p2Y){
                anguloActual = 180;
                p1Y ++;
            } else if (p1X == p2X && p1Y > p2Y){
                anguloActual = 0;
                p1Y --;
            }
             
            moverse(p1X, p1Y);
        }

        ArrayList<Integer> estadoDescenso = descender(energia);//revisar si se puede reutilizar
        energia = estadoDescenso.get(0);

        if(rescatar){
            arrayAcciones.add("rescue");
            //energia = energia - costeAcción; ?????
            //comunicar Rescate Objetivo posxposy
        }
    }

    private int girar(){
        for (int i=0; i<Math.abs(anguloActual); i+=45){
            if (anguloActual<0){
                arrayAcciones.add("rotateL");
            }
            else{
                arrayAcciones.add("rotateR");
            }
            energia = energia - costeAccion;
        }
        return energia;
    }

    private void subirAAltura(int alturaObjetivo){
        int veces = alturaObjetivo/5;

        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveUP");
            energia = energia - costeAccion*5;
            zActual = zActual + 5;
        }

        if (alturaObjetivo%5 != 0){
            arrayAcciones.add("moveUP");
            energia = energia - costeAccion*5;
            zActual = zActual + 5;
        }
    }

    private void moverse(int p1X, int p1Y){
        girar();
        int alturaCasilla = getAlturaCasilla (p1X, p1Y);// ver cómo se obtiene el mapa
        if (alturaCasilla - zActual > 0){
            subirAAltura (alturaCasilla - zActual);
        }

        arrayAcciones.add("moveF");
        energia = energia - costeAccion;
    }
    
    private void actualizarPosicionActual(int posX, int posY){
        posActual = new ArrayList<Integer>();
        posActual.add(posX);
        posActual.add(posY);
    }
    
    private void setInicio(int posX, int posY){
        inicio = new ArrayList<Integer>();
        inicio.add(posX);
        inicio.add(posY);
        
        anguloActual = 0;
        zActual = getzActual(); //ver info del mapa
        energia = 10;
    }
}
