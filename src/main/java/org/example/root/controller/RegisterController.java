package org.example.root.controller;


import org.example.root.dto.Registerdto;
import org.example.root.model.Register;
import org.example.root.model.Result;
import org.example.root.service.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/register")
public class RegisterController {

    @Autowired
    RegisterService registerService;

    @GetMapping
    public HttpEntity<?> register() {
        List<Register>registerList=registerService.getRegisters();
        return new ResponseEntity<>(registerList, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public HttpEntity<?> getRegister(@PathVariable Long id) {
        Register register = registerService.getRegisterbyid(id);
        return new ResponseEntity<>(register, HttpStatus.OK);
    }

    @PostMapping
    public HttpEntity<?> addRegister(@RequestBody Registerdto registerdto) {
        Result result = registerService.saveRegister(registerdto);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    @PutMapping("/{id}")
    public HttpEntity<?> updateRegister(@PathVariable Long id, @RequestBody Registerdto registerdto) {
        Result result = registerService.updateRegister(registerdto, id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public HttpEntity<?> deleteRegister(@PathVariable Long id) {
        Result result = registerService.deleteRegister(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
