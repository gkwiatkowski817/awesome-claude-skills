<?php
/**
 * Stopka strony.
 *
 * @package Dworek_Komorno
 */
?>
<footer class="site-footer">
	<div class="container footer-grid">
		<div class="footer-col">
			<h4 class="footer-widget-title"><?php bloginfo( 'name' ); ?></h4>
			<p><?php echo esc_html( dwk_contact( 'address' ) ); ?><br>
			tel. <?php echo esc_html( dwk_contact( 'phone' ) ); ?><br>
			<?php echo esc_html( dwk_contact( 'email' ) ); ?></p>
		</div>
		<div class="footer-col">
			<h4 class="footer-widget-title"><?php esc_html_e( 'Na skróty', 'dworek-komorno' ); ?></h4>
			<?php
			wp_nav_menu( array(
				'theme_location' => 'footer',
				'container'      => false,
				'fallback_cb'    => 'wp_page_menu',
				'depth'          => 1,
			) );
			?>
		</div>
		<div class="footer-col">
			<?php if ( is_active_sidebar( 'footer-1' ) ) { dynamic_sidebar( 'footer-1' ); } ?>
		</div>
		<div class="footer-col">
			<?php if ( is_active_sidebar( 'footer-2' ) ) { dynamic_sidebar( 'footer-2' ); } ?>
		</div>
	</div>
	<div class="footer-bottom">
		<div class="container">
			<p>&copy; <?php echo esc_html( gmdate( 'Y' ) ); ?> <?php bloginfo( 'name' ); ?> &middot;
				<a href="<?php echo esc_url( home_url( '/regulamin/' ) ); ?>"><?php esc_html_e( 'Regulamin', 'dworek-komorno' ); ?></a> &middot;
				<a href="<?php echo esc_url( home_url( '/polityka-prywatnosci/' ) ); ?>"><?php esc_html_e( 'Polityka prywatności', 'dworek-komorno' ); ?></a>
			</p>
		</div>
	</div>
</footer>

<?php wp_footer(); ?>
</body>
</html>
