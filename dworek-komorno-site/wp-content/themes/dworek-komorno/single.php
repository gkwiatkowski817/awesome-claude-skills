<?php
/**
 * Szablon pojedynczego wpisu.
 *
 * @package Dworek_Komorno
 */

get_header();
?>
<main class="section">
	<div class="container container-narrow">
		<?php while ( have_posts() ) : the_post(); ?>
			<article <?php post_class(); ?>>
				<h1 class="page-title"><?php the_title(); ?></h1>
				<p class="post-meta"><?php echo esc_html( get_the_date() ); ?></p>
				<?php if ( has_post_thumbnail() ) { the_post_thumbnail( 'large' ); } ?>
				<div class="entry-content"><?php the_content(); ?></div>
			</article>
			<?php comments_template(); ?>
		<?php endwhile; ?>
	</div>
</main>
<?php get_footer(); ?>
