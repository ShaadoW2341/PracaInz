package pl.mycomap.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.mycomap.common.ApiError;
import java.io.IOException;

public class AccountSessionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AccountSessionFilter(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionUser user) {
            boolean valid;
            try {
                valid = jdbc.query("SELECT role,account_enabled,auth_version FROM app_users WHERE id=?",
                        (rs,n) -> rs.getBoolean("account_enabled") && rs.getString("role").equals(user.role().name()) && rs.getInt("auth_version")==user.authVersion(),user.id())
                        .stream().findFirst().orElse(false);
            } catch (DataAccessException error) {
                write(response,503,"SESSION_UNAVAILABLE","Nie udało się sprawdzić sesji. Spróbuj ponownie."); return;
            }
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                String path = request.getServletPath();
                boolean publicRequest = path.equals("/api/health") || path.equals("/actuator/health")
                        || (path.startsWith("/api/auth/") && !path.equals("/api/auth/me"));
                if (!publicRequest) {
                    write(response,401,"SESSION_REVOKED","Uprawnienia konta zostały zmienione lub konto jest zablokowane. Zaloguj się ponownie."); return;
                }
            }
        }
        chain.doFilter(request,response);
    }
    private void write(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),new ApiError(code,message));
    }
}
