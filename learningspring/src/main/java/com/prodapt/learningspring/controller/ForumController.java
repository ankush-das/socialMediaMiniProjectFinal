package com.prodapt.learningspring.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.prodapt.learningspring.controller.binding.AddPostForm;
import com.prodapt.learningspring.controller.exception.ResourceNotFoundException;
import com.prodapt.learningspring.entity.Comment;
import com.prodapt.learningspring.entity.Favpost;
import com.prodapt.learningspring.entity.LikeId;
import com.prodapt.learningspring.entity.LikeRecord;
import com.prodapt.learningspring.entity.Mutedpost;
import com.prodapt.learningspring.entity.Post;
import com.prodapt.learningspring.entity.User;
import com.prodapt.learningspring.model.RegistrationForm;
import com.prodapt.learningspring.repository.CommentRepository;
import com.prodapt.learningspring.repository.FavPostRepository;
import com.prodapt.learningspring.repository.LikeCRUDRepository;
import com.prodapt.learningspring.repository.MutedPostRepository;
import com.prodapt.learningspring.repository.PostRepository;
import com.prodapt.learningspring.repository.UserRepository;
import com.prodapt.learningspring.service.DomainUserService;
import com.prodapt.learningspring.service.FavMutePostService;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/forum")
public class ForumController {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private DomainUserService domainUserService;

	@Autowired
	private LikeCRUDRepository likeCRUDRepository;

	@Autowired
	private FavPostRepository favPostRepository;

	@Autowired
	private MutedPostRepository mutedPostRepository;

	@Autowired
	private FavMutePostService favmutePostService;

	private List<User> userList;
	private List<Comment> commentList;
	private List<Favpost> favpostList;
	private List<Post> allPostfeed;
	private List<Mutedpost> mutedpostList;

	@PostConstruct
	public void init() {
		userList = new ArrayList<>();
		commentList = new ArrayList<>();
		favpostList = new ArrayList<>();
		allPostfeed = new ArrayList<>();
		mutedpostList = new ArrayList<>();
	}

	@GetMapping("/post/form")
	public String getPostForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
		AddPostForm postForm = new AddPostForm();
		User author = domainUserService.getByName(userDetails.getUsername()).get();
		postForm.setUserId(author.getId());
		model.addAttribute("postForm", postForm);
		return "forum/postForm";
	}

	@PostMapping("/post/add")
	public String addNewPost(@ModelAttribute("postForm") AddPostForm postForm, BindingResult bindingResult,
			RedirectAttributes attr) throws ServletException {
		if (bindingResult.hasErrors()) {
			System.out.println(bindingResult.getFieldErrors());
			attr.addFlashAttribute("org.springframework.validation.BindingResult.post", bindingResult);
			attr.addFlashAttribute("post", postForm);
			return "redirect:/forum/post/form";
		}
		Optional<User> user = userRepository.findById(postForm.getUserId());
		if (user.isEmpty()) {
			throw new ServletException("Something went seriously wrong and we couldn't find the user in the DB");
		}
		Post post = new Post();
		post.setAuthor(user.get());
		post.setContent(postForm.getContent());
		postRepository.save(post);

		return String.format("redirect:/forum/post/%d", post.getId());
	}

	@GetMapping("/post/{id}")
	public String postDetail(@PathVariable int id, Model model, @AuthenticationPrincipal UserDetails userDetails)
			throws ResourceNotFoundException {
		Optional<Post> post = postRepository.findById(id);
		if (post.isEmpty()) {
			throw new ResourceNotFoundException("No post with the requested ID");
		}
		model.addAttribute("post", post.get());
		model.addAttribute("likerName", userDetails.getUsername());

		model.addAttribute("commenterName", userDetails.getUsername());

		commentList = commentRepository.findAllByPostId(id);
		model.addAttribute("commentList", commentList);

		int numLikes = likeCRUDRepository.countByLikeIdPost(post.get());
		model.addAttribute("likeCount", numLikes);
		return "forum/postDetail";
	}

	@PostMapping("/post/{id}/like")
	public String postLike(@PathVariable int id, String likerName, RedirectAttributes attr) {
		LikeId likeId = new LikeId();
		likeId.setUser(userRepository.findByName(likerName).get());
		likeId.setPost(postRepository.findById(id).get());
		LikeRecord like = new LikeRecord();
		like.setLikeId(likeId);
		likeCRUDRepository.save(like);
		return String.format("redirect:/forum/post/%d", id);
	}

	@GetMapping("/register")
	public String getRegistrationForm(Model model) {
		if (!model.containsAttribute("registrationForm")) {
			model.addAttribute("registrationForm", new RegistrationForm());
		}
		return "forum/register";
	}

	@PostMapping("/register")
	public String register(@ModelAttribute("registrationForm") RegistrationForm registrationForm,
			BindingResult bindingResult, RedirectAttributes attr) {
		if (bindingResult.hasErrors()) {
			attr.addFlashAttribute("org.springframework.validation.BindingResult.registrationForm", bindingResult);
			attr.addFlashAttribute("registrationForm", registrationForm);
			return "redirect:/register";
		}
		if (!registrationForm.isValid()) {
			attr.addFlashAttribute("message", "Passwords must match");
			attr.addFlashAttribute("registrationForm", registrationForm);
			return "redirect:/register";
		}
		System.out.println(domainUserService.save(registrationForm.getUsername(), registrationForm.getPassword()));
		attr.addFlashAttribute("result", "Registration success!");
		return "redirect:/login";
	}

	@PostMapping("/post/{id}/comment")
	public String addCommentToPost(String commenterName, HttpServletRequest request, @PathVariable int id) {

		String content = request.getParameter("content");

		Optional<User> user = userRepository.findByName(commenterName);
		Optional<Post> post = postRepository.findById(id);

		if (user.isPresent() && post.isPresent()) {
			Comment comment = new Comment();
			comment.setUser(user.get());
			comment.setPost(post.get());
			comment.setContent(content);

			commentRepository.save(comment);

			return String.format("redirect:/forum/post/%d", id);
		}
		return "redirect:/forum/post/error";
	}

	// adding post to favorite

	@PostMapping("/post/{id}/fav")
	public String AddfavPost(@PathVariable int id, String commenterName) {
		Optional<User> user = userRepository.findByName(commenterName);
		Optional<Post> post = postRepository.findById(id);
		if (user.isPresent() && post.isPresent()) {

			// check if the user and post is there in the opposite table
			if (mutedPostRepository.existsByUserAndPost(user.get(), post.get())) {
				return String.format("redirect:/forum/post/%d", id);
			}

			if (favPostRepository.existsByUserAndPost(user.get(), post.get())) {
				return String.format("redirect:/forum/post/%d", id);
			} else {
				Favpost favPost = new Favpost();
				favPost.setPost(post.get());
				favPost.setUser(user.get());

				favPostRepository.save(favPost);

				return String.format("redirect:/forum/post/%d", id);

			}
		}
		return "redirect:/forum/post/error";
	}

	// Muting post

	@PostMapping("/post/{id}/mute")

	public String AddmutedPost(@PathVariable int id, String commenterName) {
		Optional<User> user = userRepository.findByName(commenterName);
		Optional<Post> post = postRepository.findById(id);

		if (user.isPresent() && post.isPresent()) {
			User commentAuthor = post.get().getAuthor();
			// checking if the user and post is there in the opposite table

			if (favPostRepository.existsByUserAndPost(user.get(), post.get())) {
				return String.format("redirect:/forum/post/%d", id);
			}

			if (mutedPostRepository.existsByUserAndPost(user.get(), post.get())) {
				return String.format("redirect:/forum/post/%d", id);

			}

			if (user.get().equals(commentAuthor)) {
				return String.format("redirect:/forum/post/%d", id);
			}
			Mutedpost mutePost = new Mutedpost();
			mutePost.setPost(post.get());
			mutePost.setUser(user.get());
			mutedPostRepository.save(mutePost);
			return String.format("redirect:/forum/post/%d", id);
		}
		return String.format("redirect:/forum/post/%d", id);
	}

	// show the favourite post feed

	@GetMapping("/post/favfeed")
	public String favpostfeed(Model model, @AuthenticationPrincipal UserDetails userDetails)
			throws ResourceNotFoundException {
		Optional<User> user = userRepository.findByName(userDetails.getUsername());
//		favpostList = favPostRepository.findAllByUser(user.get());
		favpostList = favmutePostService.findAllFavPostsByUser(user.get());
		model.addAttribute("favpostList", favpostList);
		model.addAttribute("commenterName", userDetails.getUsername());
		return "forum/favPost";
	}

	// dummy all post feed (done by other group)

	@GetMapping("/feed")
	public String allPostFeed(Model model, @AuthenticationPrincipal UserDetails userDetails) {

		Optional<User> user = userRepository.findByName(userDetails.getUsername());
//		List<Post> filteredPosts = mutedPostRepository.findAllPostsNotMutedByUser(user.get());

		List<Post> filteredPosts = favmutePostService.findAllPostsNotMutedByUser(user.get());
		model.addAttribute("allPostfeed", filteredPosts);
		return "forum/postFeed";
	}

	// delete a post from favourite feed

	@PostMapping("post/favfeed/{postId}/delete")
	public String deleteFavPost(@PathVariable int postId, String commenterName) {
		Optional<User> user = userRepository.findByName(commenterName);
		Optional<Post> post = postRepository.findById(postId);
		
		if (user.isPresent() && post.isPresent()) {
			if (favPostRepository.existsByUserAndPost(user.get(), post.get())) {
				favmutePostService.removeFavPost(user.get(),post.get());
				return String.format("redirect:/forum/post/favfeed");
			}
		}
		return "redirect:/forum/post/error";
	}

	// delete post from mute feed

	@GetMapping("/post/mutefeed")

	public String mutedpostfeed(Model model, @AuthenticationPrincipal UserDetails userDetails)

			throws ResourceNotFoundException {

		Optional<User> user = userRepository.findByName(userDetails.getUsername());

		mutedpostList = mutedPostRepository.findAllByUser(user.get());

		model.addAttribute("mutedpostList", mutedpostList);

		model.addAttribute("commenterName", userDetails.getUsername());

		return "forum/mutePost";

	}

	@PostMapping("post/mutefeed/{postId}/delete")

	public String UnmutedPost(@PathVariable int postId, String commenterName) {

		Optional<User> user = userRepository.findByName(commenterName);

		Optional<Post> post = postRepository.findById(postId);

		if (user.isPresent() && post.isPresent()) {

			if (mutedPostRepository.existsByUserAndPost(user.get(), post.get())) {

				Mutedpost mutePost = mutedPostRepository.findByUserAndPost(user.get(), post.get());

				mutedPostRepository.delete(mutePost);

				return "redirect:/forum/post/mutefeed";

			}

		}

		return "redirect:/forum/post/error";

	}
}
