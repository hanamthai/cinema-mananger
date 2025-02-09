package com.j05promax.cinema.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.j05promax.cinema.entity.User;
import com.j05promax.cinema.repo.PostgreSQLRepo;
import com.j05promax.cinema.repo.Repository;
import com.j05promax.cinema.util.log.Log;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class Customer {
    @PostMapping("/customer")
    public String createCustomer(
            HttpServletRequest request,
            HttpServletResponse response,

            @RequestParam(name = "uName", required = false, defaultValue = "") String uName,
            @RequestParam(name = "phone", required = false, defaultValue = "") String uPhone) {
        Context ctx = new Context();
        ctx.request = request;
        ctx.response = response;

        ctx = Midleware.Authenticate(ctx);
        if (!ctx.SignedIn)
            return "redirect:/auth/login";

        PostgreSQLRepo repo = PostgreSQLRepo.getInstance();
        User user = new User();
        user.FullName = uName;
        user.AdminID = ctx.UserID;
        user.PhoneNumber = uPhone;

        if (!repo.User.Create(user)) {
            return "error";
        }

        return "redirect:/customer";
    }

    @PostMapping("/update-customer/{id}")
    public String updateCustomer(
            HttpServletRequest request,
            HttpServletResponse response,

            @PathVariable(value = "id") String id,
            @RequestParam(name = "username", required = false, defaultValue = "") String username,
            @RequestParam(name = "number", required = false, defaultValue = "") String phoneNumber,
            @RequestParam(name = "status", required = false, defaultValue = "Hoạt động") String status) {
        Context ctx = new Context();
        ctx.request = request;
        ctx.response = response;

        ctx = Midleware.Authenticate(ctx);
        if (!ctx.SignedIn)
            return "redirect:/auth/login";

        PostgreSQLRepo repo = PostgreSQLRepo.getInstance();
        User user = new User();
        user.UserID = id;
        user.FullName = username;
        user.PhoneNumber = phoneNumber;
        user.Status = status;

        Repository.Error err = repo.User.Update(user);
        if (err != null) {
            AuthController.setError(ctx, err.message);
        }

        return String.format("redirect:/customer-detail/%s", user.UserID);
    }

    @GetMapping("/customer-detail/{id}")
    public String getCustomerById(
            HttpServletRequest request,
            HttpServletResponse response,

            @PathVariable(value = "id") String id,
            Model model) {

        Context ctx = new Context();
        ctx.request = request;
        ctx.response = response;

        ctx = Midleware.Authenticate(ctx);
        if (!ctx.SignedIn)
            return "redirect:/auth/login";

        PostgreSQLRepo repo = PostgreSQLRepo.getInstance();

        User user = new User();
        try {
            user = repo.User.GetByID(id);

        } catch (SQLException e) {
            new Log(e).Show();
        }

        model.addAttribute("user", user);
        return "customer-detail";
    }

    @RequestMapping(value = "/api/search-customer", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public String getGreetingWhileReturnTypeIsString(
            HttpServletRequest request,
            HttpServletResponse response,

            @RequestParam(name = "phone_or_name", required = false, defaultValue = "") String nameOrPhone) {
        Context ctx = new Context();
        ctx.request = request;
        ctx.response = response;

        ctx = Midleware.Authenticate(ctx);
        if (!ctx.SignedIn)
            return "redirect:/auth/login";

        PostgreSQLRepo repo = PostgreSQLRepo.getInstance();
        String jsonData = "{}";
        try {
            User users = repo.User.GetAll(
                nameOrPhone.strip(),
                "Hoạt động",
                new Repository.Perpage(0)
                ).get(0);
            jsonData = users.toJson();
        } catch (Exception e) {
            new Log(e).Show();
        }
        return jsonData;
    }

    @GetMapping("/customer")
    public String getCustomers(
            HttpServletRequest request,
            HttpServletResponse response,

            @RequestParam(name = "phone_or_name", required = false, defaultValue = "") String nameOrPhone,
            @RequestParam(name = "status", required = false, defaultValue = "") String status,
            @RequestParam(name = "page", required = false, defaultValue = "0") String sPage,
            Model model) {

        Context ctx = new Context();
        ctx.request = request;
        ctx.response = response;

        ctx.SetUnicodeCookie("status", status, "/customer");
        ctx.SetUnicodeCookie("phone_or_name", nameOrPhone, "/customer");

        ctx = Midleware.Authenticate(ctx);
        if (!ctx.SignedIn)
            return "redirect:/auth/login";

        PostgreSQLRepo repo = PostgreSQLRepo.getInstance();

        int counted = 0;
        try {
            counted = repo.User.CountCustomer(nameOrPhone.strip(), status);
        } catch (SQLException e) {
            new Log(e).Show();
        }

        int page = 0;
        try {
            page = Integer.parseInt(sPage);
        } catch (NumberFormatException e) {
            new Log(e).Show();
        }

        Repository.Perpage perpage = new Repository.Perpage(page);
        Boolean[] listActivepage = new Boolean[(int) Math.ceil(counted * 1.0 / perpage.maxInPage)];
        Arrays.fill(listActivepage, false);
        if (listActivepage.length != 0) {
            listActivepage[perpage.page] = true;
        }

        List<User> users = new ArrayList<>();
        try {
            users = repo.User.GetAll(
                    nameOrPhone.strip(),
                    status,
                    perpage);
        } catch (SQLException e) {
            new Log(e).Show();
        }

        model.addAttribute("countedCustomer", counted);
        model.addAttribute("pages", listActivepage);
        model.addAttribute("staffRole", ctx.UserGroup);
        model.addAttribute("staffName", ctx.UserEmail.replace("@gmail.com", "") + " (" + ctx.UserGroup + ")");
        model.addAttribute("users", users);
        return "customer";
    }
}
