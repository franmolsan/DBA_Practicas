/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package practica2_DBA;

import AppBoot.ConsoleBoot;

public class main {
    static ConsoleBoot _app;
    
    /**
    * @author: Jose Armando Albarado Mamani
    * @params: args
     */
    public static void main(String[] args) {
        _app = new ConsoleBoot("PRACTICA2_DBA", args);
        _app.selectConnection();
        _app.launchAgent("ArcelorMittal_3", MyWorldExplorer.class);
        _app.shutDown();
    }
    
}
