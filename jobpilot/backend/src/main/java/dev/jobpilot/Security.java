package dev.jobpilot;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
@Configuration
class Security {
 @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny())).build();}
 @Bean OncePerRequestFilter localOrigin(){return new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException{String host=req.getServerName(); if(!java.util.Set.of("localhost","127.0.0.1","[::1]","backend").contains(host)||"cross-site".equals(req.getHeader("Sec-Fetch-Site"))){res.sendError(403,"Local requests only");return;} String origin=req.getHeader("Origin");if(!java.util.Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())){boolean bad="cross-site".equals(req.getHeader("Sec-Fetch-Site"));if(origin!=null){try{URI u=URI.create(origin);bad|=!java.util.Set.of("localhost","127.0.0.1","[::1]").contains(u.getHost())||!java.util.Set.of(3000,8080,5173).contains(u.getPort());}catch(Exception e){bad=true;}}if(bad){res.sendError(403,"Only same-site local requests are accepted");return;}}res.setHeader("X-Content-Type-Options","nosniff");chain.doFilter(req,res);}};}
}
