import javax.servlet.http.*;

public class servletToJsp extends HttpServlet {

  private static final long serialVersionUID = 1L;

  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      // Set the attribute and Forward to hello.jsp
      request.setAttribute("servletName", "servletToJspTest");
      getServletConfig().getServletContext().getRequestDispatcher("/jsp/jsptoserv/hello.jsp").forward(request, response);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
